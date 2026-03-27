package com.example.crophud.command;

import java.util.ArrayList;
import java.util.List;

public final class ClientCommandAliasNormalizer {
    private ClientCommandAliasNormalizer() {
    }

    public static String normalize(String command) {
        if (command == null || command.isBlank()) {
            return command;
        }

        String[] rawTokens = command.trim().split("\\s+");
        if (rawTokens.length == 0) {
            return command;
        }

        List<String> tokens = new ArrayList<>(List.of(rawTokens));
        tokens.set(0, normalizeRoot(tokens.get(0)));

        if (tokens.get(0).equals("cropprice")) {
            if (tokens.size() >= 2) tokens.set(1, normalizeCropPriceSubcommand(tokens.get(1)));
            if (tokens.size() >= 3) tokens.set(2, normalizeCropName(tokens.get(2)));

        } else if (tokens.get(0).equals("hud")) {
            if (tokens.size() >= 2) tokens.set(1, normalizeHudSubcommand(tokens.get(1)));
            if (tokens.size() >= 3) {
                // Dispatch token[2] normalization based on the resolved subcommand.
                switch (tokens.get(1)) {
                    case "background" -> tokens.set(2, normalizeOnOff(tokens.get(2)));
                    case "position"   -> tokens.set(2, normalizeAnchor(tokens.get(2)));
                    case "lockcrop"   -> tokens.set(2, normalizeLockCropArg(tokens.get(2)));
                    // "pause" takes an integer — leave as-is
                }
            }
        }

        return String.join(" ", tokens);
    }

    // -------------------------------------------------------------------------
    // Root command
    // -------------------------------------------------------------------------

    private static String normalizeRoot(String token) {
        String t = token.startsWith("/") ? token.substring(1) : token;
        return switch (t.toLowerCase()) {
            case "작물가격"              -> "cropprice";
            case "허드", "hud"          -> "hud";
            // Legacy alias kept for muscle memory
            case "수확세션", "harvestsession" -> "hud";
            default                     -> t;
        };
    }

    // -------------------------------------------------------------------------
    // /hud subcommands
    // -------------------------------------------------------------------------

    private static String normalizeHudSubcommand(String token) {
        return switch (token) {
            case "초기화", "reset"            -> "reset";
            case "위치",   "position"        -> "position";
            case "편집",   "edit"            -> "edit";
            case "배경",   "background"      -> "background";
            case "대기시간", "pause"          -> "pause";
            case "작물고정", "lockcrop"       -> "lockcrop";
            // Legacy alias: old command had "허드 위치" as two tokens under harvestsession
            case "허드"                      -> "position";
            default                          -> token;
        };
    }

    private static String normalizeOnOff(String token) {
        return switch (token.toLowerCase()) {
            case "켜기", "켬", "on"   -> "on";
            case "끄기", "끔", "off"  -> "off";
            default                    -> token;
        };
    }

    // -------------------------------------------------------------------------
    // /cropprice subcommands
    // -------------------------------------------------------------------------

    private static String normalizeCropPriceSubcommand(String token) {
        return switch (token) {
            case "설정" -> "set";
            case "조회" -> "get";
            case "목록" -> "list";
            default     -> token;
        };
    }

    // -------------------------------------------------------------------------
    // Anchor names
    // -------------------------------------------------------------------------

    private static String normalizeAnchor(String token) {
        return switch (token) {
            case "좌상단", "좌측상단"       -> "top_left";
            case "우상단", "우측상단"       -> "top_right";
            case "중앙상단", "중앙상부"     -> "center_top";
            case "중앙"                     -> "center";
            case "좌하단", "좌측하단"       -> "bottom_left";
            case "우하단", "우측하단"       -> "bottom_right";
            case "중앙하단", "중앙하부"     -> "center_bottom";
            default                         -> token;
        };
    }

    // -------------------------------------------------------------------------
    // Lock-crop argument (crop name or "해제"/"off")
    // -------------------------------------------------------------------------

    private static String normalizeLockCropArg(String token) {
        if ("해제".equals(token) || "off".equalsIgnoreCase(token)) return "off";
        return token; // Korean crop names are passed as-is to the greedyString argument handler
    }

    // -------------------------------------------------------------------------
    // Crop names
    // -------------------------------------------------------------------------

    private static String normalizeCropName(String token) {
        return switch (token) {
            case "밀"               -> "wheat";
            case "당근"             -> "carrot";
            case "감자"             -> "potato";
            case "비트", "비트루트" -> "beetroot";
            case "네더와트"         -> "netherwart";
            case "코코아", "코코아콩" -> "cocoa";
            case "사탕수수"         -> "sugarcane";
            case "선인장"           -> "cactus";
            case "호박"             -> "pumpkin";
            case "수박"             -> "melon";
            default                 -> token;
        };
    }
}
