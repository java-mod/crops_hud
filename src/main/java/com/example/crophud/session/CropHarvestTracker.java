package com.example.crophud.session;

import com.example.crophud.client.ClientSessionState;
import com.example.crophud.hud.HudPositionStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class CropHarvestTracker {
    /**
     * Maximum milliseconds after a crop-break event during which inventory deltas are
     * attributed to that harvest.  Items arriving outside this window (e.g. received
     * from another player while the session is active but the farmer is standing still)
     * are silently folded into the baseline and never counted as harvests.
     *
     * 1 500 ms gives enough headroom for server round-trip + item-pickup animation while
     * still being short enough to reject most "gift while farming" false positives.
     */
    private static final long HARVEST_COLLECT_WINDOW_MS = 1_500L;

    private final CropPriceStore priceStore;
    private final HudPositionStore hudPositionStore;
    private final SessionData session = new SessionData();

    /** When non-null, only this crop is tracked; events for other crops are ignored. */
    private Item lockedCropItem = null;

    public CropHarvestTracker(CropPriceStore priceStore, HudPositionStore hudPositionStore) {
        this.priceStore = priceStore;
        this.hudPositionStore = hudPositionStore;
    }

    public void initialize() {
        ClientPlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);
        UseBlockCallback.EVENT.register(this::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        publishState(Util.getMeasuringTimeMs());
    }

    public void reset() {
        session.reset();
        publishState(Util.getMeasuringTimeMs());
    }

    public void setHudAnchor(com.example.crophud.hud.HudAnchor anchor) {
        hudPositionStore.setPosition(anchor);
        publishState(Util.getMeasuringTimeMs());
    }

    // -------------------------------------------------------------------------
    // Crop lock
    // -------------------------------------------------------------------------

    /**
     * Locks tracking to the given crop item.
     * Only harvests of this crop will be counted until {@link #unlockCrop()} is called.
     */
    public void lockCrop(Item crop) {
        lockedCropItem = crop;
    }

    /** Removes the crop lock so all crop types are tracked again. */
    public void unlockCrop() {
        lockedCropItem = null;
    }

    /** Returns {@code true} when a crop lock is active. */
    public boolean isLocked() {
        return lockedCropItem != null;
    }

    /** Returns the currently locked {@link Item}, or {@code null} if not locked. */
    public Item getLockedCropItem() {
        return lockedCropItem;
    }

    private void onBlockBreak(ClientWorld world, ClientPlayerEntity player, BlockPos pos, BlockState state) {
        Item cropItem = resolveQualifiedHarvest(state, world, pos);
        if (cropItem != null) {
            onCropBroken(player, pos, cropItem);
        }
    }

    private ActionResult onUseBlock(net.minecraft.entity.player.PlayerEntity player, World world, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (!(world instanceof ClientWorld clientWorld) || hand != net.minecraft.util.Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = clientWorld.getBlockState(pos);
        Item cropItem = resolveQualifiedHarvest(state, clientWorld, pos);
        if (cropItem != null) {
            onCropBroken(player, pos, cropItem);
        }

        return ActionResult.PASS;
    }

    /**
     * Called when the player breaks or right-clicks a mature crop.
     * Handles session timing and crop identification.
     * Actual item counting is done every tick in onClientTick via continuous inventory monitoring.
     */
    private void onCropBroken(net.minecraft.entity.player.PlayerEntity player, BlockPos pos, Item cropItem) {
        long now = Util.getMeasuringTimeMs();

        if (session.isDuplicate(pos, now)) {
            return;
        }

        // If a crop is locked, ignore events for any other crop.
        if (lockedCropItem != null && cropItem != lockedCropItem) {
            return;
        }

        // Detect crop change BEFORE reset() alters currentCropItem.
        // baselineNeeded is true when the session hasn't started yet, or the incoming crop
        // differs from the current one. In both cases lastKnownCropCount must be re-snapshotted
        // so the tick loop doesn't mistake pre-existing items for harvested ones.
        boolean baselineNeeded = !session.isActive() || session.currentCropItem != cropItem;

        if (session.shouldRestartForCrop(cropItem)) {
            session.reset();
        }

        session.pauseIfIdle(now, hudPositionStore.getIdlePauseMillis());

        if (!session.isActive()) {
            session.currentSegmentStartTimeMs = now;
        }

        if (baselineNeeded) {
            // Snapshot both normal and special baselines so pre-existing items
            // (with or without a custom name) are not mistaken for new harvests.
            SpecialDropRegistry.SpecialDropDef baselineDef  = SpecialDropRegistry.get(cropItem);
            Item                               baselineAlias = SpecialDropRegistry.getNormalDropAlias(cropItem);
            session.lastKnownCropCount   = countNormalItems(player, cropItem, baselineDef, baselineAlias);
            session.lastKnownSpecialCount = baselineDef != null ? countSpecialItemsByDef(player, baselineDef) : 0;
        }

        session.lastTrackedPos = pos.asLong();
        session.lastTrackedAtMs = now;
        session.lastHarvestTimeMs = now;
        session.currentCropItem = cropItem;
        session.cropSet = true;

        publishState(now);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }

        long now = Util.getMeasuringTimeMs();

        // Continuous inventory monitoring — normal and special items tracked separately.
        //
        // Normal items:  same crop Item type, NOT matching the special-drop name → regular harvest.
        // Special items: looked up via SpecialDropRegistry (may be a different item ID entirely,
        //                e.g. pumpkin_pie for a pumpkin session) + display name substring match.
        //
        // Separating the two prevents a "달달한 호박" pumpkin_pie from inflating the
        // regular pumpkin harvest count, and vice-versa.
        //
        // ANTI-SPOOFING: deltas are only credited when the tick falls within
        // HARVEST_COLLECT_WINDOW_MS of the last actual crop-break event.
        // Outside that window the baselines still silently follow the current inventory,
        // so items received as gifts during idle time are absorbed into the baseline and
        // never mistaken for harvests when the player resumes farming.
        if (session.currentCropItem != null) {
            SpecialDropRegistry.SpecialDropDef specialDef  =
                    SpecialDropRegistry.get(session.currentCropItem);
            Item                               normalAlias =
                    SpecialDropRegistry.getNormalDropAlias(session.currentCropItem);

            int normalNow  = countNormalItems(client.player, session.currentCropItem, specialDef, normalAlias);
            int specialNow = specialDef != null
                    ? countSpecialItemsByDef(client.player, specialDef) : 0;

            boolean withinHarvestWindow = session.isActive()
                    && session.lastHarvestTimeMs > 0L
                    && (now - session.lastHarvestTimeMs) <= HARVEST_COLLECT_WINDOW_MS;

            if (withinHarvestWindow) {
                int normalDelta  = normalNow  - session.lastKnownCropCount;
                int specialDelta = specialNow - session.lastKnownSpecialCount;
                if (normalDelta  > 0) session.harvestedByItem.merge(session.currentCropItem, normalDelta, Integer::sum);
                if (specialDelta > 0) session.specialDrops += specialDelta;
            }

            // Always update baselines — outside the window this silently absorbs any
            // externally received items so they don't contaminate future harvest deltas.
            session.lastKnownCropCount   = normalNow;
            session.lastKnownSpecialCount = specialNow;
        }

        session.pauseIfIdle(now, hudPositionStore.getIdlePauseMillis());
        publishState(now);
    }

    /**
     * Counts regular (non-special) harvest items of the given type, plus an optional
     * alias item that should also be counted as part of the same normal harvest.
     *
     * <p>For crops whose special drop uses the <em>same</em> item ID (e.g. carrot "아삭한 당근"),
     * stacks whose display name contains the special-drop name are excluded from the normal
     * count so they are not double-counted as regular harvests.</p>
     *
     * <p>For crops whose special drop uses a <em>different</em> item ID (e.g. pumpkin → pumpkin_pie),
     * there is no overlap and the {@code specialDef} filter is effectively a no-op for this method.</p>
     *
     * <p>{@code normalAlias} handles crops like melon where the server may drop both the
     * block item ({@code minecraft:melon}) and the slice item ({@code minecraft:melon_slice})
     * as regular harvest — both are summed into the same normal count.</p>
     *
     * <p>The cursor slot is included to avoid counting in-flight drag operations.</p>
     */
    private static int countNormalItems(net.minecraft.entity.player.PlayerEntity player, Item item,
                                        SpecialDropRegistry.SpecialDropDef specialDef,
                                        Item normalAlias) {
        int count = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (isNormalCropItem(stack, item, specialDef)) count += stack.getCount();
            else if (normalAlias != null && isNormalCropItem(stack, normalAlias, specialDef)) count += stack.getCount();
        }
        if (isNormalCropItem(player.getOffHandStack(), item, specialDef)) {
            count += player.getOffHandStack().getCount();
        } else if (normalAlias != null && isNormalCropItem(player.getOffHandStack(), normalAlias, specialDef)) {
            count += player.getOffHandStack().getCount();
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (isNormalCropItem(cursor, item, specialDef)) count += cursor.getCount();
        else if (normalAlias != null && isNormalCropItem(cursor, normalAlias, specialDef)) count += cursor.getCount();
        return count;
    }

    /**
     * Returns {@code true} when the stack is a normal (non-special) harvest item.
     *
     * <p>A stack is considered normal when:
     * <ul>
     *   <li>Its item type matches the expected crop item, AND</li>
     *   <li>It is NOT identified as a same-ID special drop (i.e. the special def targets a
     *       different item ID, OR the stack's display name does not contain the special name).</li>
     * </ul>
     */
    private static boolean isNormalCropItem(ItemStack stack, Item item,
                                            SpecialDropRegistry.SpecialDropDef specialDef) {
        if (stack.isEmpty() || stack.getItem() != item) return false;
        // Same-ID special: exclude stacks whose display name matches the special-drop name.
        if (specialDef != null && specialDef.item() == item
                && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)
                && stack.getName().getString().contains(specialDef.name())) {
            return false;
        }
        return true;
    }

    /**
     * Counts stacks that match the given special-drop definition: correct item type AND
     * a display name that contains the expected name substring.
     */
    private static int countSpecialItemsByDef(net.minecraft.entity.player.PlayerEntity player,
                                              SpecialDropRegistry.SpecialDropDef def) {
        int count = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (matchesSpecialDef(stack, def)) count += stack.getCount();
        }
        if (matchesSpecialDef(player.getOffHandStack(), def)) {
            count += player.getOffHandStack().getCount();
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (matchesSpecialDef(cursor, def)) count += cursor.getCount();
        return count;
    }

    private static boolean matchesSpecialDef(ItemStack stack, SpecialDropRegistry.SpecialDropDef def) {
        if (stack.isEmpty() || stack.getItem() != def.item()) return false;
        if (!stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) return false;
        return stack.getName().getString().contains(def.name());
    }

    private void publishState(long now) {
        String lockedId = "";
        if (lockedCropItem != null) {
            Identifier id = Registries.ITEM.getId(lockedCropItem);
            lockedId = id == null ? "" : id.toString();
        }
        ClientSessionState.update(
                session.activeMillis(now),
                session.totalHarvestedUnits(),
                session.totalValue(priceStore),
                hudPositionStore.getAnchor(),
                session.isActive(),
                session.currentCropId(),
                session.specialDrops,
                lockedId);
    }

    private Item resolveQualifiedHarvest(BlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();

        if (block instanceof CropBlock cropBlock) {
            if (!cropBlock.isMature(state)) {
                return null;
            }
            if (block == Blocks.WHEAT) return Items.WHEAT;
            if (block == Blocks.CARROTS) return Items.CARROT;
            if (block == Blocks.POTATOES) return Items.POTATO;
            if (block == Blocks.BEETROOTS) return Items.BEETROOT;
        }

        if (block instanceof NetherWartBlock) {
            return state.contains(NetherWartBlock.AGE) && state.get(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? Items.NETHER_WART : null;
        }

        if (block instanceof CocoaBlock) {
            return state.get(Properties.AGE_2) >= 2 ? Items.COCOA_BEANS : null;
        }

        if (block instanceof SugarCaneBlock) {
            return world.getBlockState(pos.down()).getBlock() == Blocks.SUGAR_CANE ? Items.SUGAR_CANE : null;
        }

        if (block instanceof CactusBlock) {
            return world.getBlockState(pos.down()).getBlock() == Blocks.CACTUS ? Items.CACTUS : null;
        }

        if (block == Blocks.PUMPKIN) return Items.PUMPKIN;
        if (block == Blocks.MELON) return Items.MELON;
        return null;
    }

    private static final class SessionData {
        private long accumulatedActiveMillis;
        private long currentSegmentStartTimeMs;
        private long lastHarvestTimeMs;
        private long lastTrackedAtMs;
        private long lastTrackedPos = Long.MIN_VALUE;
        private final Map<Item, Integer> harvestedByItem = new HashMap<>();
        private Item currentCropItem = Items.WHEAT;

        // Baseline counts for continuous inventory delta tracking.
        // lastKnownCropCount    — items WITHOUT a custom name (regular harvest)
        // lastKnownSpecialCount — items WITH a custom name (special drops)
        // Both are updated every tick so re-activation and crop-switches start correctly.
        private int lastKnownCropCount   = 0;
        private int lastKnownSpecialCount = 0;

        // True once a crop has been explicitly set via onCropBroken.
        // Used by shouldRestartForCrop instead of the fragile harvestedByItem.isEmpty() check,
        // which could fail when items haven't arrived in inventory yet at the time of a crop switch.
        private boolean cropSet = false;

        // Special drops accumulated via inventory delta monitoring during this session.
        int specialDrops = 0;

        private long activeMillis(long now) {
            if (!isActive()) {
                return accumulatedActiveMillis;
            }
            if (now <= currentSegmentStartTimeMs) {
                return accumulatedActiveMillis;
            }
            return accumulatedActiveMillis + (now - currentSegmentStartTimeMs);
        }

        private boolean pauseIfIdle(long now, long idlePauseMillis) {
            if (!isActive()) return false;
            if (lastHarvestTimeMs <= 0L || now - lastHarvestTimeMs < idlePauseMillis) return false;
            accumulatedActiveMillis += Math.max(0L, lastHarvestTimeMs - currentSegmentStartTimeMs);
            currentSegmentStartTimeMs = 0L;
            return true;
        }

        private boolean isActive() {
            return currentSegmentStartTimeMs > 0L;
        }

        private boolean isDuplicate(BlockPos pos, long now) {
            return pos.asLong() == lastTrackedPos && now - lastTrackedAtMs <= 250L;
        }

        private boolean shouldRestartForCrop(Item cropItem) {
            // Use cropSet instead of harvestedByItem.isEmpty(): if items haven't arrived in
            // inventory yet when the player breaks a different crop, harvestedByItem would be
            // empty and the restart would be skipped, leaving stale session data in place.
            return cropSet && currentCropItem != cropItem;
        }

        private int totalHarvestedUnits() {
            return harvestedByItem.values().stream().mapToInt(Integer::intValue).sum();
        }

        private BigDecimal totalValue(CropPriceStore priceStore) {
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<Item, Integer> entry : harvestedByItem.entrySet()) {
                total = total.add(priceStore.getPrice(entry.getKey()).multiply(BigDecimal.valueOf(entry.getValue())));
            }
            return total;
        }

        private String currentCropId() {
            Identifier id = Registries.ITEM.getId(currentCropItem);
            return id == null ? "" : id.toString();
        }

        private void reset() {
            accumulatedActiveMillis = 0L;
            currentSegmentStartTimeMs = 0L;
            lastHarvestTimeMs = 0L;
            lastTrackedAtMs = 0L;
            lastTrackedPos = Long.MIN_VALUE;
            currentCropItem = Items.WHEAT;
            harvestedByItem.clear();
            lastKnownCropCount   = 0;
            lastKnownSpecialCount = 0;
            cropSet      = false;
            specialDrops = 0;
        }
    }
}
