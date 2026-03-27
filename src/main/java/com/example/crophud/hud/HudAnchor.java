package com.example.crophud.hud;

import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

public enum HudAnchor {
    TOP_LEFT("top_left"),
    TOP_RIGHT("top_right"),
    CENTER_TOP("center_top"),
    CENTER("center"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right"),
    CENTER_BOTTOM("center_bottom");

    private final String id;

    HudAnchor(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Text displayName() {
        return Text.translatable("crophud.anchor." + id);
    }

    public static HudAnchor fromId(String id) {
        if (id == null) {
            return TOP_RIGHT;
        }

        String normalized = normalize(id);
        for (HudAnchor value : values()) {
            if (value.id.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return TOP_RIGHT;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(HudAnchor::id).toList();
    }

    public static boolean isValid(String id) {
        String normalized = normalize(id);
        return Arrays.stream(values()).anyMatch(value -> value.id.equalsIgnoreCase(normalized));
    }

    public static List<String> commandAliases() {
        return List.of(
                "top_left", "top_right", "center_top", "center",
                "bottom_left", "bottom_right", "center_bottom",
                "좌상단", "우상단", "중앙상단", "중앙", "좌하단", "우하단", "중앙하단",
                "좌측상단", "우측상단", "중앙상부", "좌측하단", "우측하단", "중앙하부"
        );
    }

    public static String normalize(String id) {
        return switch (id.toLowerCase()) {
            case "좌상단", "좌측상단" -> "top_left";
            case "우상단", "우측상단" -> "top_right";
            case "중앙상단", "중앙상부" -> "center_top";
            case "중앙" -> "center";
            case "좌하단", "좌측하단" -> "bottom_left";
            case "우하단", "우측하단" -> "bottom_right";
            case "중앙하단", "중앙하부" -> "center_bottom";
            default -> id;
        };
    }
}
