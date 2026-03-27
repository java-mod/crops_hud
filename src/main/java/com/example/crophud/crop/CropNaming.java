package com.example.crophud.crop;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CropNaming {
    private static final Map<String, Item> KOREAN_ALIASES = createKoreanAliases();
    private static final Map<Item, String> KOREAN_NAMES = createKoreanNames();

    private CropNaming() {
    }

    public static Map<String, Item> koreanAliases() {
        return KOREAN_ALIASES;
    }

    public static List<String> koreanCommandNames() {
        return List.copyOf(KOREAN_ALIASES.keySet());
    }

    public static String koreanName(Item item) {
        return KOREAN_NAMES.getOrDefault(item, "알 수 없음");
    }

    private static Map<String, Item> createKoreanAliases() {
        Map<String, Item> map = new LinkedHashMap<>();
        map.put("밀", Items.WHEAT);
        map.put("당근", Items.CARROT);
        map.put("감자", Items.POTATO);
        map.put("비트", Items.BEETROOT);
        map.put("비트루트", Items.BEETROOT);
        map.put("네더와트", Items.NETHER_WART);
        map.put("코코아", Items.COCOA_BEANS);
        map.put("코코아콩", Items.COCOA_BEANS);
        map.put("사탕수수", Items.SUGAR_CANE);
        map.put("선인장", Items.CACTUS);
        map.put("호박", Items.PUMPKIN);
        map.put("수박", Items.MELON);
        return map;
    }

    private static Map<Item, String> createKoreanNames() {
        Map<Item, String> map = new LinkedHashMap<>();
        map.put(Items.WHEAT, "밀");
        map.put(Items.CARROT, "당근");
        map.put(Items.POTATO, "감자");
        map.put(Items.BEETROOT, "비트루트");
        map.put(Items.NETHER_WART, "네더와트");
        map.put(Items.COCOA_BEANS, "코코아콩");
        map.put(Items.SUGAR_CANE, "사탕수수");
        map.put(Items.CACTUS, "선인장");
        map.put(Items.PUMPKIN, "호박");
        map.put(Items.MELON, "수박");
        return map;
    }
}
