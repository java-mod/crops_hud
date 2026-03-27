package com.example.crophud.command;

import com.example.crophud.CropHudMod;
import com.example.crophud.crop.CropNaming;
import com.example.crophud.session.CropPriceStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CropPriceCommand {
    private static final SuggestionProvider<FabricClientCommandSource> CROP_SUGGESTIONS =
            (context, builder) -> CommandSource.suggestMatching(CropNaming.koreanCommandNames(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> SET_INPUT_SUGGESTIONS =
            (context, builder) -> {
                String remaining = builder.getRemaining();
                String trimmed = remaining == null ? "" : remaining.trim();
                if (trimmed.isEmpty() || !trimmed.contains(" ")) {
                    return CommandSource.suggestMatching(CropNaming.koreanCommandNames(), builder);
                }
                return builder.buildFuture();
            };

    private CropPriceCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(createRoot("cropprice", "set", "get", "list"));
        dispatcher.register(createRoot("작물가격", "설정", "조회", "목록"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createRoot(String rootLiteral, String setLiteral, String getLiteral, String listLiteral) {
        return ClientCommandManager.literal(rootLiteral)
                .executes(CropPriceCommand::executeRoot)
                .then(createSetLiteral(setLiteral))
                .then(createGetLiteral(getLiteral))
                .then(createListLiteral(listLiteral));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createSetLiteral(String literal) {
        return ClientCommandManager.literal(literal)
                .then(ClientCommandManager.argument("input", StringArgumentType.greedyString())
                        .suggests(SET_INPUT_SUGGESTIONS)
                        .executes(CropPriceCommand::executeSet));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createGetLiteral(String literal) {
        return ClientCommandManager.literal(literal)
                .then(ClientCommandManager.argument("crop", StringArgumentType.greedyString())
                        .suggests(CROP_SUGGESTIONS)
                        .executes(CropPriceCommand::executeGet));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> createListLiteral(String literal) {
        return ClientCommandManager.literal(literal)
                .executes(CropPriceCommand::executeList);
    }

    private static int executeRoot(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.translatable("crophud.command.cropprice.usage"));
        return 1;
    }

    private static int executeSet(CommandContext<FabricClientCommandSource> context) {
        CropPriceStore priceStore = CropHudMod.priceStore();
        String input = StringArgumentType.getString(context, "input").trim();
        
        // Parse input: "crop price" or "crop price fortune"
        String[] parts = input.split("\\s+");
        if (parts.length < 2) {
            context.getSource().sendError(Text.translatable("crophud.command.cropprice.missing_price", input, input));
            return 0;
        }

        String crop = parts[0];
        BigDecimal price = parsePrice(parts[1]);
        int fortuneLevel = 0;
        
        if (parts.length >= 3) {
            try {
                fortuneLevel = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                // Ignore invalid fortune level
            }
        }
        
        if (crop.isEmpty()) {
            context.getSource().sendError(Text.translatable("crophud.command.cropprice.unknown_crop", crop));
            return 0;
        }
        if (price == null || price.signum() < 0) {
            context.getSource().sendError(Text.translatable("crophud.command.cropprice.invalid_price", parts[1]));
            return 0;
        }

        if (!priceStore.setPrice(crop, price, fortuneLevel)) {
            context.getSource().sendError(Text.translatable("crophud.command.cropprice.unknown_crop", crop));
            return 0;
        }

        if (fortuneLevel > 0) {
            context.getSource().sendFeedback(Text.translatable("crophud.command.cropprice.set_with_fortune", 
                koreanCropName(priceStore, crop), formatPrice(price), fortuneLevel));
        } else {
            context.getSource().sendFeedback(Text.translatable("crophud.command.cropprice.set", koreanCropName(priceStore, crop), formatPrice(price)));
        }
        return 1;
    }

    private static int executeGet(CommandContext<FabricClientCommandSource> context) {
        CropPriceStore priceStore = CropHudMod.priceStore();
        String crop = StringArgumentType.getString(context, "crop").trim();
        BigDecimal price = priceStore.getPrice(crop);
        if (price == null) {
            context.getSource().sendError(Text.translatable("crophud.command.cropprice.unknown_crop", crop));
            return 0;
        }

        context.getSource().sendFeedback(Text.translatable("crophud.command.cropprice.get", koreanCropName(priceStore, crop), formatPrice(price)));
        return 1;
    }

    private static int executeList(CommandContext<FabricClientCommandSource> context) {
        Map<Item, BigDecimal> lines = CropHudMod.priceStore().allPrices();
        if (lines.isEmpty()) {
            context.getSource().sendFeedback(Text.translatable("crophud.command.cropprice.empty"));
            return 1;
        }

        context.getSource().sendFeedback(Text.translatable("crophud.command.cropprice.list_header"));
        lines.entrySet().stream()
                .sorted(Comparator.comparing(entry -> CropNaming.koreanName(entry.getKey())))
                .forEach(entry -> context.getSource().sendFeedback(Text.translatable("crophud.command.cropprice.list_entry", CropNaming.koreanName(entry.getKey()), formatPrice(entry.getValue()))));
        return 1;
    }

    private static String koreanCropName(CropPriceStore priceStore, String crop) {
        return CropNaming.koreanName(priceStore.resolveCrop(crop));
    }

    private static BigDecimal parsePrice(String input) {
        try {
            return new BigDecimal(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formatPrice(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
