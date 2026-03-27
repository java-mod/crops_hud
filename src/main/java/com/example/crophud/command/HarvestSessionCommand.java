package com.example.crophud.command;

import com.example.crophud.CropHudMod;
import com.example.crophud.client.HudEditScreen;
import com.example.crophud.hud.HudAnchor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

public final class HarvestSessionCommand {
    private static final SuggestionProvider<FabricClientCommandSource> POSITION_SUGGESTIONS =
            (context, builder) -> CommandSource.suggestMatching(HudAnchor.commandAliases(), builder);

    private HarvestSessionCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(createRoot("harvestsession", "reset", "hud", "position", "edit"));
        dispatcher.register(createRoot("수확세션",        "초기화",  "허드",  "위치",     "편집"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createRoot(
            String rootLiteral, String resetLiteral, String hudLiteral,
            String positionLiteral, String editLiteral) {
        return ClientCommandManager.literal(rootLiteral)
                .executes(context -> {
                    context.getSource().sendFeedback(
                            Text.translatable("crophud.command.harvestsession.usage"));
                    return 1;
                })
                .then(createResetLiteral(resetLiteral))
                .then(createHudLiteral(hudLiteral, positionLiteral, editLiteral));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createResetLiteral(String literal) {
        return ClientCommandManager.literal(literal)
                .executes(context -> {
                    CropHudMod.tracker().reset();
                    context.getSource().sendFeedback(
                            Text.translatable("crophud.command.harvestsession.reset"));
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createHudLiteral(
            String literal, String positionLiteral, String editLiteral) {
        return ClientCommandManager.literal(literal)
                .executes(context -> {
                    context.getSource().sendFeedback(
                            Text.translatable("crophud.command.harvestsession.hud_usage"));
                    return 1;
                })
                .then(createPositionLiteral(positionLiteral))
                .then(createEditLiteral(editLiteral));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createPositionLiteral(String literal) {
        return ClientCommandManager.literal(literal)
                .executes(context -> {
                    context.getSource().sendFeedback(
                            Text.translatable("crophud.command.harvestsession.position_usage"));
                    return 1;
                })
                .then(ClientCommandManager.argument("anchor", StringArgumentType.greedyString())
                        .suggests(POSITION_SUGGESTIONS)
                        .executes(context -> {
                            String anchorId = StringArgumentType.getString(context, "anchor").trim();
                            if (!HudAnchor.isValid(anchorId)) {
                                context.getSource().sendError(Text.translatable(
                                        "crophud.command.harvestsession.position_invalid", anchorId));
                                return 0;
                            }
                            HudAnchor anchor = HudAnchor.fromId(anchorId);
                            CropHudMod.tracker().setHudAnchor(anchor);
                            context.getSource().sendFeedback(Text.translatable(
                                    "crophud.command.harvestsession.position_set", anchor.displayName()));
                            return 1;
                        }));
    }

    /** Opens the drag-and-drop HUD editor screen. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> createEditLiteral(String literal) {
        return ClientCommandManager.literal(literal)
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        // Schedule for the next tick so the chat screen finishes closing
                        // before we open the editor — otherwise setScreen() would be
                        // overwritten by the chat-close logic.
                        client.send(() -> {
                            if (!(client.currentScreen instanceof HudEditScreen)) {
                                client.setScreen(new HudEditScreen());
                            }
                        });
                    }
                    return 1;
                });
    }
}
