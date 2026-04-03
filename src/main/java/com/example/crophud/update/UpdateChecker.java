package com.example.crophud.update;

import com.example.crophud.CropHudMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 서버/월드 접속 시 GitHub Releases API를 조회하여 새 버전을 확인하고,
 * 새 버전이 있으면 자동 설치 없이 사용자에게 업데이트 필요 알림만 전송합니다.
 */
public final class UpdateChecker {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/java-mod/crops_hud/releases/latest";
    private static final String GITHUB_RELEASES_URL =
            "https://github.com/java-mod/crops_hud/releases/latest";
    private static final String MOD_ID = "crophud";

    /** 세션당 1회만 체크 */
    private static volatile boolean checked = false;

    private UpdateChecker() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static void checkAsync(MinecraftClient client) {
        if (checked) return;
        checked = true;

        Thread.ofVirtual().name("crophud-update-check").start(() -> {
            try {
                runCheck(client);
            } catch (Exception e) {
                CropHudMod.LOGGER.warn("[UpdateChecker] 업데이트 확인 실패: {}", e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    private static void runCheck(MinecraftClient client) throws Exception {
        // 1. 현재 버전
        String currentRaw = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
        if (currentRaw == null) return;

        String current = stripMcSuffix(currentRaw);

        // 2. GitHub API 호출
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", MOD_ID + "-update-checker")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return;

        // 3. 응답 파싱
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String tagName = json.get("tag_name").getAsString();
        String latest  = tagName.startsWith("v") ? tagName.substring(1) : tagName;

        // 4. 버전 비교
        if (!isNewer(latest, current)) return;

        // 5. 릴리스 링크 확인 후 사용자 알림 (메인 스레드)
        String releaseUrl = extractReleaseUrl(json);
        String ver = latest;
        client.execute(() -> {
            if (client.player == null) return;
            client.player.sendMessage(
                    Text.literal("[Crops HUD] ").formatted(Formatting.AQUA)
                            .append(Text.literal("새 버전 ").formatted(Formatting.WHITE))
                            .append(Text.literal("v" + ver).formatted(Formatting.YELLOW))
                            .append(Text.literal(" 이(가) 있습니다.").formatted(Formatting.WHITE)),
                    false
            );
            client.player.sendMessage(
                    Text.literal("[Crops HUD] ").formatted(Formatting.AQUA)
                            .append(Text.literal("자동 업데이트는 비활성화되어 있습니다. 직접 업데이트해 주세요: ").formatted(Formatting.GREEN))
                            .append(Text.literal(releaseUrl).formatted(Formatting.YELLOW)),
                    false
            );
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String extractReleaseUrl(JsonObject json) {
        if (json.has("html_url")) {
            return json.get("html_url").getAsString();
        }

        JsonArray assets = json.getAsJsonArray("assets");
        if (assets != null) {
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                if (asset.has("browser_download_url")) {
                    return asset.get("browser_download_url").getAsString();
                }
            }
        }

        return GITHUB_RELEASES_URL;
    }

    private static String stripMcSuffix(String version) {
        int idx = version.indexOf('+');
        return idx >= 0 ? version.substring(0, idx) : version;
    }

    /**
     * {@code latest}가 {@code current}보다 높은 버전이면 {@code true}.
     * 각 세그먼트를 정수로 비교합니다 (1.0.10 > 1.0.9 등).
     */
    private static boolean isNewer(String latest, String current) {
        try {
            String[] a = latest.split("\\.");
            String[] b = current.split("\\.");
            int len = Math.max(a.length, b.length);
            for (int i = 0; i < len; i++) {
                int av = i < a.length ? Integer.parseInt(a[i]) : 0;
                int bv = i < b.length ? Integer.parseInt(b[i]) : 0;
                if (av > bv) return true;
                if (av < bv) return false;
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }
}
