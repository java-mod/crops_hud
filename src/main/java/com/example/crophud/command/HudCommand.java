package com.example.crophud.command;

import com.example.crophud.CropHudMod;
import com.example.crophud.client.HudEditScreen;
import com.example.crophud.crop.CropNaming;
import com.example.crophud.hud.HudAnchor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

/**
 * Root command: {@code /hud} (English) / {@code /허드} (Korean).
 *
 * <pre>
 *   /hud reset                  — reset the current harvest session
 *   /hud position <anchor>      — set HUD position to a preset anchor
 *   /hud edit                   — open the drag-and-drop HUD editor
 *   /hud background on|off      — toggle the card background
 *   /hud pause <seconds>        — set idle pause delay (1–300 s)
 *   /hud lockcrop <crop>        — lock tracking to a specific crop
 *   /hud lockcrop off           — remove crop lock
 * </pre>
 */
public final class HudCommand {
    private static final SuggestionProvider<FabricClientCommandSource> POSITION_SUGGESTIONS =
            (context, builder) -> CommandSource.suggestMatching(HudAnchor.commandAliases(), builder);

    private HudCommand() {
    }

    private static final SuggestionProvider<FabricClientCommandSource> CROP_SUGGESTIONS =
            (context, builder) -> CommandSource.suggestMatching(CropNaming.koreanCommandNames(), builder);

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(buildRoot("hud",  "reset", "position", "edit", "background", "pause", "lockcrop"));
        dispatcher.register(buildRoot("허드", "초기화", "위치",     "편집", "배경",        "대기시간", "작물고정"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildRoot(
            String root, String resetLit, String positionLit, String editLit, String bgLit,
            String pauseLit, String lockCropLit) {
        return ClientCommandManager.literal(root)
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.translatable("crophud.command.hud.usage"));
                    return 1;
                })
                .then(buildReset(resetLit))
                .then(buildPosition(positionLit))
                .then(buildEdit(editLit))
                .then(buildBackground(bgLit))
                .then(buildPause(pauseLit))
                .then(buildLockCrop(lockCropLit));
    }

    // /hud reset
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildReset(String lit) {
        return ClientCommandManager.literal(lit)
                .executes(ctx -> {
                    CropHudMod.tracker().reset();
                    ctx.getSource().sendFeedback(Text.translatable("crophud.command.hud.reset"));
                    return 1;
                });
    }

    // /hud position <anchor>
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildPosition(String lit) {
        return ClientCommandManager.literal(lit)
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.translatable("crophud.command.hud.position_usage"));
                    return 1;
                })
                .then(ClientCommandManager.argument("anchor", StringArgumentType.greedyString())
                        .suggests(POSITION_SUGGESTIONS)
                        .executes(ctx -> {
                            String anchorId = StringArgumentType.getString(ctx, "anchor").trim();
                            if (!HudAnchor.isValid(anchorId)) {
                                ctx.getSource().sendError(Text.translatable(
                                        "crophud.command.hud.position_invalid", anchorId));
                                return 0;
                            }
                            HudAnchor anchor = HudAnchor.fromId(anchorId);
                            CropHudMod.tracker().setHudAnchor(anchor);
                            ctx.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.hud.position_set", anchor.displayName()));
                            return 1;
                        }));
    }

    // /hud edit  — scheduled for the next tick so the chat screen closes first
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildEdit(String lit) {
        return ClientCommandManager.literal(lit)
                .executes(ctx -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.send(() -> {
                            if (!(client.currentScreen instanceof HudEditScreen)) {
                                client.setScreen(new HudEditScreen());
                            }
                        });
                    }
                    return 1;
                });
    }

    // /hud pause <seconds>  — configures idle pause delay
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildPause(String lit) {
        return ClientCommandManager.literal(lit)
                .executes(ctx -> {
                    int current = CropHudMod.hudPositionStore().getIdlePauseSeconds();
                    ctx.getSource().sendFeedback(Text.translatable(
                            "crophud.command.hud.pause_status", current));
                    return 1;
                })
                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 300))
                        .executes(ctx -> {
                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            CropHudMod.hudPositionStore().setIdlePauseSeconds(seconds);
                            ctx.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.hud.pause_set", seconds));
                            return 1;
                        }));
    }

    // /hud lockcrop <crop>  — lock tracking to a specific crop (or "off" to unlock)
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildLockCrop(String lit) {
        return ClientCommandManager.literal(lit)
                .executes(ctx -> {
                    if (CropHudMod.tracker().isLocked()) {
                        Item locked = CropHudMod.tracker().getLockedCropItem();
                        ctx.getSource().sendFeedback(Text.translatable(
                                "crophud.command.hud.lockcrop_current",
                                CropNaming.koreanName(locked)));
                    } else {
                        ctx.getSource().sendFeedback(Text.translatable(
                                "crophud.command.hud.lockcrop_usage"));
                    }
                    return 1;
                })
                .then(ClientCommandManager.literal("off")
                        .executes(ctx -> {
                            CropHudMod.tracker().unlockCrop();
                            ctx.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.hud.lockcrop_off"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("해제")
                        .executes(ctx -> {
                            CropHudMod.tracker().unlockCrop();
                            ctx.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.hud.lockcrop_off"));
                            return 1;
                        }))
                .then(ClientCommandManager.argument("crop", StringArgumentType.greedyString())
                        .suggests(CROP_SUGGESTIONS)
                        .executes(ctx -> {
                            String cropName = StringArgumentType.getString(ctx, "crop").trim();
                            Item crop = CropNaming.koreanAliases().get(cropName);
                            if (crop == null) {
                                ctx.getSource().sendError(Text.translatable(
                                        "crophud.command.cropprice.unknown_crop", cropName));
                                return 0;
                            }
                            CropHudMod.tracker().lockCrop(crop);
                            ctx.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.hud.lockcrop_set",
                                    CropNaming.koreanName(crop)));
                            return 1;
                        }));
    }

    // /hud background on|off
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildBackground(String lit) {
        return ClientCommandManager.literal(lit)
                .executes(ctx -> {
                    // No argument — show current state
                    boolean current = CropHudMod.hudPositionStore().isShowBackground();
                    ctx.getSource().sendFeedback(Text.translatable(
                            "crophud.command.hud.background_status",
                            Text.translatable(current
                                    ? "crophud.command.hud.background_on"
                                    : "crophud.command.hud.background_off")));
                    return 1;
                })
                .then(ClientCommandManager.literal("on")
                        .executes(ctx -> {
                            CropHudMod.hudPositionStore().setShowBackground(true);
                            ctx.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.hud.background_set",
                                    Text.translatable("crophud.command.hud.background_on")));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("off")
                        .executes(ctx -> {
                            CropHudMod.hudPositionStore().setShowBackground(false);
                            ctx.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.hud.background_set",
                                    Text.translatable("crophud.command.hud.background_off")));
                            return 1;
                        }));
    }
}
