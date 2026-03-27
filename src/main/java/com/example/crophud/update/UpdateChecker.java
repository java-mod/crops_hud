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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 서버/월드 접속 시 GitHub Releases API를 조회하여 새 버전을 확인하고,
 * 새 버전이 있으면 영구 모드 디렉토리에 다운로드한 뒤 재시작을 안내합니다.
 *
 * <p>Feather Client 지원:
 * <ul>
 *   <li>%APPDATA%/.feather/user-mods/{MC_VERSION}-fabric/ 가 존재하면 해당 경로를 사용.</li>
 *   <li>이 경로는 Feather가 세션 시작마다 feather-mods/ 로 복사하는 영구 저장소입니다.</li>
 *   <li>구버전 JAR은 이 경로에서 즉시 삭제 (파일 잠금 없음).</li>
 * </ul>
 */
public final class UpdateChecker {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/java-mod/crops_hud/releases/latest";
    private static final String MOD_ID = "crophud";
    private static final String MC_VERSION = "1.21.4";

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

        // 2. 영구 설치 디렉토리 결정
        //    Feather: %APPDATA%/.feather/user-mods/1.21.4-fabric/
        //    일반:   getGameDir()/mods
        Path installDir = resolveInstallDir();
        CropHudMod.LOGGER.info("[UpdateChecker] 설치 경로: {}", installDir);

        // 2-1. 이전 세션의 구버전 JAR 정리 (installDir 기준 — 잠금 없음)
        cleanupOldVersions(installDir, current, null);

        // 3. GitHub API 호출
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", MOD_ID + "-updater")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return;

        // 4. 응답 파싱
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String tagName = json.get("tag_name").getAsString();
        String latest  = tagName.startsWith("v") ? tagName.substring(1) : tagName;

        // 5. 버전 비교
        if (!isNewer(latest, current)) return;

        // 6. 다운로드 URL 추출
        String downloadUrl = findDownloadUrl(json.getAsJsonArray("assets"));
        if (downloadUrl == null) return;

        // 7. 새 JAR 다운로드
        String newName = MOD_ID + "-" + latest + "+mc" + MC_VERSION + ".jar";
        Path   newJar  = installDir.resolve(newName);

        if (!Files.exists(newJar)) {
            CropHudMod.LOGGER.info("[UpdateChecker] v{} 다운로드 중...", latest);
            downloadFile(downloadUrl, newJar, http);
            CropHudMod.LOGGER.info("[UpdateChecker] 다운로드 완료 → {}", newName);
        }

        // 8. 구버전 즉시 삭제 (installDir 의 파일은 잠기지 않음)
        cleanupOldVersions(installDir, latest, newJar);

        // 9. 사용자 알림 (메인 스레드)
        String ver = latest;
        client.execute(() -> {
            if (client.player == null) return;
            client.player.sendMessage(
                    Text.literal("[Crops HUD] ").formatted(Formatting.AQUA)
                            .append(Text.literal("새 버전 ").formatted(Formatting.WHITE))
                            .append(Text.literal("v" + ver).formatted(Formatting.YELLOW))
                            .append(Text.literal(" 이(가) 설치됐습니다.").formatted(Formatting.WHITE)),
                    false
            );
            client.player.sendMessage(
                    Text.literal("[Crops HUD] ").formatted(Formatting.AQUA)
                            .append(Text.literal("재시작하면 업데이트가 적용됩니다.").formatted(Formatting.GREEN)),
                    false
            );
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * 영구 모드 설치 디렉토리를 반환합니다.
     * Feather Client가 감지되면 user-mods 경로를, 아니면 Fabric 기본 mods 경로를 반환합니다.
     */
    private static Path resolveInstallDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            Path featherUserMods = Path.of(appdata, ".feather", "user-mods", MC_VERSION + "-fabric");
            if (Files.isDirectory(featherUserMods)) {
                return featherUserMods;
            }
        }
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    /**
     * 지정 디렉토리에서 현재 버전 이외의 crophud JAR을 삭제합니다.
     * @param keepVersion 보존할 버전 문자열 (예: "1.0.12")
     * @param excludePath null이 아니면 이 경로도 삭제 제외
     */
    private static void cleanupOldVersions(Path dir, String keepVersion, Path excludePath) {
        String keepPrefix = MOD_ID + "-" + keepVersion + "+mc" + MC_VERSION;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(MOD_ID + "-")
                                && (n.endsWith(".jar") || n.endsWith(".temp.jar"))
                                && !n.startsWith(keepPrefix)
                                && !p.equals(excludePath);
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            CropHudMod.LOGGER.info("[UpdateChecker] 구버전 삭제: {}", p.getFileName());
                        } catch (IOException e) {
                            CropHudMod.LOGGER.warn("[UpdateChecker] 구버전 삭제 실패: {}", p.getFileName());
                        }
                    });
        } catch (IOException e) {
            CropHudMod.LOGGER.warn("[UpdateChecker] 디렉토리 스캔 실패: {}", e.getMessage());
        }
    }

    private static String findDownloadUrl(JsonArray assets) {
        for (JsonElement el : assets) {
            JsonObject asset = el.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.startsWith(MOD_ID) && name.endsWith(".jar")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private static void downloadFile(String url, Path dest, HttpClient http) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", MOD_ID + "-updater")
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("다운로드 실패 (HTTP " + res.statusCode() + ")");
        }
        Files.copy(res.body(), dest, StandardCopyOption.REPLACE_EXISTING);
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
