package com.example.crophud.client;

import com.example.crophud.CropHudMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Loads a user-supplied {@code config/crophud/background.png} and draws it stretched
 * to fill the HUD card.
 *
 * <p>Two Minecraft rendering APIs used here changed signature across the
 * 1.21.4–1.21.11 range this mod targets (see {@link CropHudClientMod} for the
 * same problem with HUD registration): {@code DrawContext#drawTexture} took a
 * {@code Function<Identifier, RenderLayer>} pre-1.21.7 and a {@code RenderPipeline}
 * from 1.21.7 onward, and {@code NativeImageBackedTexture}'s constructor gained a
 * label {@code Supplier<String>} parameter in 1.21.5. Both are resolved once via
 * reflection, mirroring the compat pattern already used for HUD layer registration.</p>
 *
 * <p><b>Important:</b> {@code net.minecraft.*} class/member names used in reflection here
 * must go through {@link FabricLoader}'s {@link MappingResolver}, keyed by their stable
 * <em>intermediary</em> IDs (e.g. {@code net.minecraft.class_1921}, {@code method_62277}) —
 * looked up once from the local Yarn mapping files this mod is built against — rather than
 * plain string literals like {@code "net.minecraft.client.render.RenderLayer"}. A raw
 * {@code Class.forName("net.minecraft.client.render.RenderLayer")} only happens to resolve
 * in a Loom dev environment (which runs under "named"/Yarn mappings); in a real launched
 * game, Fabric Loader remaps this mod's own compiled references from named to intermediary
 * at build time via {@code remapJar}, but a bare string in {@code Class.forName} is opaque to
 * that remapper and is never rewritten — so at runtime no class has that literal name and it
 * throws {@code ClassNotFoundException}. Class references that ARE ordinary compile-time type
 * references (like {@code DrawContext.class} or {@code NativeImageBackedTexture.class} used
 * elsewhere in this file) don't have this problem, since remapJar rewrites those correctly;
 * only bare reflective name strings need MappingResolver.</p>
 */
final class HudBackgroundTexture {
    private static final Identifier TEXTURE_ID = Identifier.of("crophud", "custom_background");
    private static final Path IMAGE_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve(CropHudMod.MOD_ID).resolve("background.png");

    private static boolean compatResolved = false;
    private static Method drawTextureMethod;
    private static Object drawTextureFirstArg;

    private static NativeImageBackedTexture loadedTexture;
    private static int imageWidth;
    private static int imageHeight;
    private static FileTime loadedMtime;
    private static boolean loadFailed = false;

    /** Short description of the most recent load/draw failure, surfaced by the settings panel; null when healthy. */
    private static volatile String lastError;

    private HudBackgroundTexture() {
    }

    /** Returns {@code true} once a valid custom background image is loaded and ready to draw. */
    static boolean isAvailable() {
        return resolveCompat() && ensureLoaded();
    }

    /** Short description of the most recent load/draw failure, or {@code null} if the last attempt succeeded. */
    static String lastError() {
        return lastError;
    }

    /** Draws the custom background image stretched into (x, y, width, height); no-op if unavailable. */
    static void draw(DrawContext ctx, int x, int y, int width, int height) {
        if (!isAvailable()) return;
        try {
            drawTextureMethod.invoke(ctx, drawTextureFirstArg, TEXTURE_ID,
                    x, y, 0.0f, 0.0f, width, height, imageWidth, imageHeight, imageWidth, imageHeight, 0xFFFFFFFF);
            lastError = null;
        } catch (Exception e) {
            CropHudMod.LOGGER.error("Failed to draw custom HUD background image", e);
            lastError = "draw failed: " + describe(e);
        }
    }

    private static String describe(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }

    // -------------------------------------------------------------------------
    // Image loading (lazy, reloads when background.png changes on disk)
    // -------------------------------------------------------------------------

    private static boolean ensureLoaded() {
        if (!Files.exists(IMAGE_PATH)) {
            unload();
            loadFailed = false;
            loadedMtime = null;
            return false;
        }

        try {
            FileTime mtime = Files.getLastModifiedTime(IMAGE_PATH);
            if (mtime.equals(loadedMtime)) {
                return loadedTexture != null;
            }
            load(mtime);
            return loadedTexture != null;
        } catch (IOException e) {
            CropHudMod.LOGGER.error("Failed to read custom HUD background image at {}", IMAGE_PATH, e);
            loadFailed = true;
            lastError = "read failed: " + describe(e);
            return false;
        }
    }

    private static void load(FileTime mtime) {
        NativeImage image;
        try (InputStream in = Files.newInputStream(IMAGE_PATH)) {
            image = NativeImage.read(in);
        } catch (Exception e) {
            CropHudMod.LOGGER.error("Failed to decode custom HUD background image at {}", IMAGE_PATH, e);
            unload();
            loadFailed = true;
            loadedMtime = mtime;
            lastError = "decode failed: " + describe(e);
            return;
        }

        try {
            NativeImageBackedTexture texture = createTexture(image);
            texture.upload();
            applyBilinearFilter(texture);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);

            unload();
            loadedTexture = texture;
            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            loadedMtime = mtime;
            loadFailed = false;
            lastError = null;
        } catch (Exception e) {
            CropHudMod.LOGGER.error("Failed to register custom HUD background image", e);
            image.close();
            loadFailed = true;
            loadedMtime = mtime;
            lastError = "register failed: " + describe(e);
        }
    }

    /**
     * Best-effort: enables bilinear (smooth) minification/magnification so the stretched
     * card background doesn't look blocky. {@code AbstractTexture#setFilter(boolean, boolean)}
     * (intermediary {@code net.minecraft.class_1044#method_4527}) existed through 1.21.10 but
     * was removed in 1.21.11 in favor of a GpuSampler-based API; on versions where it's gone
     * this silently leaves the (blockier) default filtering.
     */
    private static void applyBilinearFilter(NativeImageBackedTexture texture) {
        try {
            String runtimeName = mapMethodName("net.minecraft.class_1044", "method_4527", "(ZZ)V");
            Method setFilter = texture.getClass().getMethod(runtimeName, boolean.class, boolean.class);
            setFilter.invoke(texture, true, false);
        } catch (Exception ignored) {
            // Not available on this Minecraft version — default filtering is used instead.
        }
    }

    private static NativeImageBackedTexture createTexture(NativeImage image) throws ReflectiveOperationException {
        try {
            Constructor<NativeImageBackedTexture> ctor =
                    NativeImageBackedTexture.class.getConstructor(NativeImage.class);
            return ctor.newInstance(image);
        } catch (NoSuchMethodException legacyCtorMissing) {
            Constructor<NativeImageBackedTexture> ctor =
                    NativeImageBackedTexture.class.getConstructor(Supplier.class, NativeImage.class);
            Supplier<String> label = () -> "crophud custom background";
            return ctor.newInstance(label, image);
        }
    }

    private static void unload() {
        if (loadedTexture != null) {
            loadedTexture.close();
        }
        loadedTexture = null;
    }

    // -------------------------------------------------------------------------
    // Cross-version DrawContext#drawTexture resolution (resolved once)
    // -------------------------------------------------------------------------

    private static boolean resolveCompat() {
        if (compatResolved) return drawTextureMethod != null;
        compatResolved = true;

        try {
            resolveModernPipeline();
            return true;
        } catch (Exception ignored) {
            CropHudMod.LOGGER.info("RenderPipeline-based texture drawing unavailable; trying legacy RenderLayer API");
        }

        try {
            resolveLegacyRenderLayer();
            return true;
        } catch (Exception e) {
            CropHudMod.LOGGER.warn("Custom HUD background images are not supported on this Minecraft version", e);
            lastError = "unsupported on this Minecraft version: " + describe(e);
            return false;
        }
    }

    /** 1.21.7+: {@code net.minecraft.client.gl.RenderPipelines} (intermediary {@code class_10799}). */
    private static void resolveModernPipeline() throws Exception {
        Class<?> pipelinesClass = resolveClass("net.minecraft.client.gl.RenderPipelines", "net.minecraft.class_10799");
        // The GUI_TEXTURED field's type, com.mojang.blaze3d.pipeline.RenderPipeline, is part of
        // Mojang's separate (unobfuscated) blaze3d library, so it keeps its real name in every
        // mapping namespace — safe to Class.forName directly, unlike net.minecraft.* classes.
        Class<?> pipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline");
        String fieldName = mapFieldName("net.minecraft.class_10799", "field_56883",
                "Lcom/mojang/blaze3d/pipeline/RenderPipeline;");
        Field guiTexturedField = pipelinesClass.getField(fieldName);

        drawTextureMethod = findDrawTextureMethod(pipelineClass);
        drawTextureFirstArg = guiTexturedField.get(null);
    }

    /** Pre-1.21.7: {@code net.minecraft.client.render.RenderLayer} (intermediary {@code class_1921}). */
    private static void resolveLegacyRenderLayer() throws Exception {
        Class<?> renderLayerClass = resolveClass("net.minecraft.client.render.RenderLayer", "net.minecraft.class_1921");
        String methodName = mapMethodName("net.minecraft.class_1921", "method_62277",
                "(Lnet/minecraft/class_2960;)Lnet/minecraft/class_1921;");
        Method guiTextured = renderLayerClass.getMethod(methodName, Identifier.class);

        InvocationHandler handler = (proxy, method, args) -> {
            if ("apply".equals(method.getName()) && method.getDeclaringClass() != Object.class) {
                return guiTextured.invoke(null, args[0]);
            }
            return switch (method.getName()) {
                case "toString" -> "crophud$GuiTexturedFunction";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        };
        Object functionProxy = Proxy.newProxyInstance(
                Function.class.getClassLoader(), new Class[]{Function.class}, handler);

        drawTextureMethod = findDrawTextureMethod(Function.class);
        drawTextureFirstArg = functionProxy;
    }

    /**
     * Finds the {@code drawTexture} overload shaped
     * {@code (firstParam, Identifier, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color)}.
     *
     * <p>There is a shorter, 12-arg overload without the trailing {@code color} tint, but its
     * own Yarn documentation states its region size is constrained to match the on-screen
     * rectangle size ("the width and height of the region are the same as the dimensions of
     * the rectangle") — i.e. it does not actually stretch. This 13-arg one is the general
     * region-to-rectangle blit and is the one that supports an arbitrary on-screen size.</p>
     *
     * <p>Matched purely by parameter-count/type shape rather than by name — this method's own
     * name is subject to the same intermediary-vs-named problem described in the class-level
     * doc comment, but {@code DrawContext.class} itself is a normal compile-time type reference
     * (remapped correctly), so enumerating its methods and filtering by shape sidesteps needing
     * to know its runtime name at all. This exact 13-argument shape is unique to drawTexture.</p>
     */
    private static Method findDrawTextureMethod(Class<?> firstParamType) throws NoSuchMethodException {
        for (Method method : DrawContext.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 13 && params[0].isAssignableFrom(firstParamType)
                    && params[1] == Identifier.class
                    && params[2] == int.class && params[3] == int.class
                    && params[4] == float.class && params[5] == float.class
                    && params[6] == int.class && params[7] == int.class
                    && params[8] == int.class && params[9] == int.class
                    && params[10] == int.class && params[11] == int.class
                    && params[12] == int.class) {
                return method;
            }
        }
        throw new NoSuchMethodException("Unable to find 13-arg DrawContext#drawTexture for " + firstParamType);
    }

    // -------------------------------------------------------------------------
    // MappingResolver helpers — see the class-level doc comment for why these,
    // rather than plain Class.forName/getMethod with named-mapping strings, are
    // required for reflection against net.minecraft.* members.
    // -------------------------------------------------------------------------

    private static Class<?> resolveClass(String namedName, String intermediaryName) throws ClassNotFoundException {
        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
        String runtimeName = resolver.mapClassName("intermediary", intermediaryName);
        try {
            return Class.forName(runtimeName);
        } catch (ClassNotFoundException e) {
            // Fall back to the named form in case this environment's resolver has no
            // intermediary mapping data (e.g. an unusual dev setup) but does recognize named.
            return Class.forName(resolver.mapClassName("named", namedName));
        }
    }

    private static String mapMethodName(String ownerIntermediary, String nameIntermediary, String descriptorIntermediary) {
        return FabricLoader.getInstance().getMappingResolver()
                .mapMethodName("intermediary", ownerIntermediary, nameIntermediary, descriptorIntermediary);
    }

    private static String mapFieldName(String ownerIntermediary, String nameIntermediary, String descriptorIntermediary) {
        return FabricLoader.getInstance().getMappingResolver()
                .mapFieldName("intermediary", ownerIntermediary, nameIntermediary, descriptorIntermediary);
    }
}
