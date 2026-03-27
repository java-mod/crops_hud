package com.example.crophud.session;

import com.example.crophud.CropHudMod;
import com.example.crophud.crop.CropNaming;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CropPriceStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "crophud_prices.json";

    private final Path filePath;
    private final Map<String, Item> cropAliases;
    private final Map<Item, BigDecimal> prices = new HashMap<>();
    private final Map<Item, Integer> fortuneLevels = new HashMap<>();
    private boolean initialized = false;

    public CropPriceStore() {
        this.filePath = FabricLoader.getInstance().getConfigDir().resolve(CropHudMod.MOD_ID).resolve(FILE_NAME);
        this.cropAliases = createAliases();
    }

    public void load() {
        prices.clear();
        fortuneLevels.clear();
        
        // Apply default prices first
        applyDefaultPrices();
        
        // Load custom prices from file
        if (Files.exists(filePath)) {
            try {
                String raw = Files.readString(filePath);
                JsonObject root = GSON.fromJson(raw, JsonObject.class);
                if (root != null) {
                    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                        Item item = resolveCrop(entry.getKey());
                        if (item == null) {
                            continue;
                        }
                        if (entry.getValue().isJsonObject()) {
                            JsonObject cropData = entry.getValue().getAsJsonObject();
                            if (cropData.has("price")) {
                                try {
                                    prices.put(item, new BigDecimal(cropData.get("price").getAsString()));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            if (cropData.has("fortune")) {
                                fortuneLevels.put(item, cropData.get("fortune").getAsInt());
                            }
                        } else if (entry.getValue().isJsonPrimitive()) {
                            // Legacy format: just price
                            try {
                                prices.put(item, new BigDecimal(entry.getValue().getAsString()));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            } catch (Exception e) {
                CropHudMod.LOGGER.error("Failed to load crop prices from {}", filePath, e);
            }
        }
        
        initialized = true;
        save(); // Save to ensure file exists with all prices
    }

    private void applyDefaultPrices() {
        // Set default prices for all supported crops
        prices.put(Items.WHEAT, new BigDecimal("10"));
        prices.put(Items.CARROT, new BigDecimal("15"));
        prices.put(Items.POTATO, new BigDecimal("12"));
        prices.put(Items.BEETROOT, new BigDecimal("20"));
        prices.put(Items.NETHER_WART, new BigDecimal("25"));
        prices.put(Items.COCOA_BEANS, new BigDecimal("30"));
        prices.put(Items.SUGAR_CANE, new BigDecimal("8"));
        prices.put(Items.CACTUS, new BigDecimal("5"));
        prices.put(Items.PUMPKIN, new BigDecimal("50"));
        prices.put(Items.MELON, new BigDecimal("35"));
    }

    public void save() {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<Item, BigDecimal> entry : prices.entrySet()) {
                Identifier id = Registries.ITEM.getId(entry.getKey());
                JsonObject cropData = new JsonObject();
                cropData.addProperty("price", entry.getValue().toPlainString());
                Integer fortune = fortuneLevels.get(entry.getKey());
                if (fortune != null && fortune > 0) {
                    cropData.addProperty("fortune", fortune);
                }
                root.add(id.toString(), cropData);
            }
            Files.writeString(filePath, GSON.toJson(root));
        } catch (IOException e) {
            CropHudMod.LOGGER.error("Failed to save crop prices to {}", filePath, e);
        }
    }

    public boolean setPrice(String cropName, BigDecimal price) {
        return setPrice(cropName, price, 0);
    }

    public boolean setPrice(String cropName, BigDecimal price, int fortuneLevel) {
        Item cropItem = resolveCrop(cropName);
        if (cropItem == null) {
            return false;
        }
        prices.put(cropItem, price);
        if (fortuneLevel > 0) {
            fortuneLevels.put(cropItem, fortuneLevel);
        }
        save();
        return true;
    }

    public int getFortuneLevel(Item item) {
        return fortuneLevels.getOrDefault(item, 0);
    }

    public BigDecimal getPrice(String cropName) {
        Item cropItem = resolveCrop(cropName);
        if (cropItem == null) {
            return null;
        }
        return prices.getOrDefault(cropItem, BigDecimal.ZERO);
    }

    public BigDecimal getPrice(Item item) {
        return prices.getOrDefault(item, BigDecimal.ZERO);
    }

    public Map<Item, BigDecimal> allPrices() {
        return Collections.unmodifiableMap(prices);
    }

    public Set<String> supportedCropNames() {
        return cropAliases.keySet();
    }

    public Item resolveCrop(String cropName) {
        if (cropName == null) {
            return null;
        }

        String normalized = cropName.toLowerCase(Locale.ROOT);
        Item aliased = cropAliases.get(normalized);
        if (aliased != null) {
            return aliased;
        }

        Identifier id = Identifier.tryParse(normalized);
        if (id == null) {
            return null;
        }

        if (!Registries.ITEM.containsId(id)) {
            return null;
        }

        Item item = Registries.ITEM.get(id);
        if (!cropAliases.containsValue(item)) {
            return null;
        }

        return item;
    }

    private static Map<String, Item> createAliases() {
        Map<String, Item> map = new HashMap<>();
        map.put("wheat", Items.WHEAT);
        map.put("minecraft:wheat", Items.WHEAT);
        map.put("carrot", Items.CARROT);
        map.put("carrots", Items.CARROT);
        map.put("minecraft:carrot", Items.CARROT);
        map.put("potato", Items.POTATO);
        map.put("potatoes", Items.POTATO);
        map.put("minecraft:potato", Items.POTATO);
        map.put("beetroot", Items.BEETROOT);
        map.put("beetroots", Items.BEETROOT);
        map.put("minecraft:beetroot", Items.BEETROOT);
        map.put("nether_wart", Items.NETHER_WART);
        map.put("netherwart", Items.NETHER_WART);
        map.put("minecraft:nether_wart", Items.NETHER_WART);
        map.put("cocoa", Items.COCOA_BEANS);
        map.put("cocoa_beans", Items.COCOA_BEANS);
        map.put("minecraft:cocoa_beans", Items.COCOA_BEANS);
        map.put("sugar_cane", Items.SUGAR_CANE);
        map.put("sugarcane", Items.SUGAR_CANE);
        map.put("minecraft:sugar_cane", Items.SUGAR_CANE);
        map.put("cactus", Items.CACTUS);
        map.put("minecraft:cactus", Items.CACTUS);
        map.put("pumpkin", Items.PUMPKIN);
        map.put("minecraft:pumpkin", Items.PUMPKIN);
        map.put("melon", Items.MELON);
        map.put("melon_block", Items.MELON);
        map.put("minecraft:melon", Items.MELON);
        map.put("melon_slice", Items.MELON);
        map.put("minecraft:melon_slice", Items.MELON);
        map.putAll(CropNaming.koreanAliases());
        return map;
    }
}
