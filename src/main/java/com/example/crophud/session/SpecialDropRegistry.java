package com.example.crophud.session;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps each harvestable crop item to its server-defined special drop,
 * and optionally to an additional normal-drop alias item.
 *
 * <p>Special drops are server plugin items that share the same or a different vanilla
 * item ID from the regular crop, distinguished only by their custom display name.
 * For example, harvesting pumpkins may rarely yield a {@code minecraft:pumpkin_pie}
 * named "달달한 호박" — a completely different item type from the regular {@code
 * minecraft:pumpkin} drops.</p>
 *
 * <p>Some crops produce a <em>different</em> vanilla item as their regular harvest
 * (e.g. melon blocks drop melon slices).  A {@link #getNormalDropAlias} entry allows
 * the tracker to count that secondary item as part of the normal harvest total, in
 * addition to the primary {@code currentCropItem}.</p>
 *
 * <p>Detection strategy in the inventory tick loop:</p>
 * <ol>
 *   <li>Look up the {@link SpecialDropDef} for the current session crop.</li>
 *   <li>Scan all inventory slots for stacks matching {@code def.item()} whose
 *       {@link net.minecraft.item.ItemStack#getName() display name} contains
 *       {@code def.name()}.</li>
 *   <li>Track the delta between ticks; a positive delta = new special drop.</li>
 * </ol>
 */
public final class SpecialDropRegistry {
    private SpecialDropRegistry() {}

    /**
     * Defines the expected item type and display-name substring for a special drop.
     *
     * @param item the vanilla item ID the server uses for this special drop
     * @param name substring that must appear in the stack's display name
     */
    public record SpecialDropDef(Item item, String name) {}

    private static final Map<Item, SpecialDropDef> REGISTRY     = new HashMap<>();

    /**
     * Maps a session crop item to a secondary item that should also be counted as
     * normal (non-special) harvest.
     *
     * <p>Example: when the session crop is {@code minecraft:melon} (the block item),
     * the server may drop both melon blocks <em>and</em> melon slices as regular
     * harvest — both should contribute to the normal harvest total.</p>
     */
    private static final Map<Item, Item>           NORMAL_ALIAS = new HashMap<>();

    static {
        // Crops whose special drop shares the SAME item ID (distinguished by custom name only)
        REGISTRY.put(Items.CARROT,      new SpecialDropDef(Items.CARROT,       "아삭한 당근"));
        REGISTRY.put(Items.BEETROOT,    new SpecialDropDef(Items.BEETROOT,     "달콤한 비트"));

        // Crops whose special drop uses a DIFFERENT item ID
        REGISTRY.put(Items.WHEAT,       new SpecialDropDef(Items.BREAD,        "구수한 밀"));
        REGISTRY.put(Items.POTATO,      new SpecialDropDef(Items.BAKED_POTATO, "포슬한 감자"));
        REGISTRY.put(Items.NETHER_WART, new SpecialDropDef(Items.DRIED_KELP,   "알싸한 네더 사마귀"));
        REGISTRY.put(Items.COCOA_BEANS, new SpecialDropDef(Items.COOKIE,       "향긋한 코코아콩"));
        REGISTRY.put(Items.PUMPKIN,     new SpecialDropDef(Items.PUMPKIN_PIE,  "달달한 호박"));

        // Melon: session crop is Items.MELON (the block item), but the server can also
        // drop Items.MELON_SLICE as a regular harvest → count both.
        // Special drop is MELON_SLICE with a custom name (same-ID special, like carrot/beetroot).
        REGISTRY.put(Items.MELON,       new SpecialDropDef(Items.MELON_SLICE,  "아삭한 수박"));
        NORMAL_ALIAS.put(Items.MELON,   Items.MELON_SLICE);
    }

    /**
     * Returns the special-drop definition for the given crop item, or {@code null} if
     * the crop has no configured special drop (e.g. sugar cane, cactus).
     */
    public static SpecialDropDef get(Item cropItem) {
        return REGISTRY.get(cropItem);
    }

    /**
     * Returns the secondary normal-harvest item to count alongside {@code cropItem},
     * or {@code null} if no alias is configured.
     *
     * <p>The alias item is counted as a regular (non-special) harvest in addition to
     * the primary {@code cropItem}.  Special-name items of the alias type are still
     * excluded via the {@link SpecialDropDef} filter.</p>
     */
    public static Item getNormalDropAlias(Item cropItem) {
        return NORMAL_ALIAS.get(cropItem);
    }
}
