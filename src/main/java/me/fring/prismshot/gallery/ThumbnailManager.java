/*
 * MIT License
 *
 * Copyright (c) 2026 Fring (Prismshot fork)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.fring.prismshot.gallery;

import com.mojang.blaze3d.platform.NativeImage;
import me.fring.prismshot.config.Config;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.TextureManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages thumbnail generation, caching, and GPU texture lifecycle.
 * <p>
 * Thumbnails are generated asynchronously on a background thread, cached as PNG files
 * on disk, and uploaded to GPU textures on the render thread. An LRU eviction strategy
 * keeps memory usage bounded.
 */
public class ThumbnailManager {

    private static final Path CACHE_DIR = FabricLoader.getInstance().getConfigDir().resolve("prismshot/thumbnails");

    /** Futures for thumbnails currently being loaded (background thread → GPU upload). */
    private static final Map<Path, CompletableFuture<NativeImage>> loading = new ConcurrentHashMap<>();

    /** GPU textures currently loaded, keyed by screenshot path. */
    private static final Map<Path, Identifier> textures = new ConcurrentHashMap<>();

    /** Thumbnail dimensions (width, height) keyed by screenshot path. */
    private static final Map<Path, int[]> dimensions = new ConcurrentHashMap<>();

    /** LRU access order for eviction. */
    private static final java.util.LinkedHashMap<Path, Long> accessOrder = new java.util.LinkedHashMap<>(16, 0.75f, true);

    private static volatile boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException ignored) {
        }
    }

    /**
     * Returns the cache path for a given screenshot file.
     * The cache key includes the file size to invalidate when the file changes.
     */
    private static Path cachePath(Path original) {
        try {
            long size = Files.size(original);
            return CACHE_DIR.resolve(Integer.toHexString(original.toAbsolutePath().toString().hashCode()) + "_" + size + ".png");
        } catch (IOException e) {
            return CACHE_DIR.resolve(Integer.toHexString(original.toAbsolutePath().toString().hashCode()) + ".png");
        }
    }

    /**
     * Requests a thumbnail for the given screenshot. If the thumbnail is already cached on disk,
     * it is loaded directly. Otherwise, the full image is loaded, scaled down, and cached.
     * The actual GPU texture upload happens on the render thread via {@link #tick()}.
     */
    public static void requestThumbnail(Path file) {
        if (textures.containsKey(file) || loading.containsKey(file)) {
            return;
        }

        CompletableFuture<NativeImage> future = CompletableFuture.supplyAsync(() -> {
            try {
                Path cached = cachePath(file);
                if (Config.GALLERY_CACHE_ENABLED && Files.exists(cached)) {
                    try (InputStream is = Files.newInputStream(cached)) {
                        return NativeImage.read(is);
                    }
                }

                try (InputStream is = Files.newInputStream(file)) {
                    NativeImage full = NativeImage.read(is);
                    NativeImage thumb = scaleDown(full, Config.GALLERY_THUMBNAIL_SIZE);
                    full.close();

                    if (Config.GALLERY_CACHE_ENABLED) {
                        try {
                            thumb.writeToFile(cached);
                        } catch (IOException ignored) {
                        }
                    }
                    return thumb;
                }
            } catch (Exception e) {
                return null;
            }
        }, Util.backgroundExecutor());

        loading.put(file, future);
    }

    /**
     * Scales a NativeImage down to fit within targetSize x targetSize, preserving aspect ratio.
     * Uses area-averaging (box filter) for high quality downscaling.
     */
    private static NativeImage scaleDown(NativeImage src, int targetSize) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) {
            return new NativeImage(targetSize, targetSize, false);
        }

        // Generate at 2x the display size for sharper appearance when downscaled by GPU
        int renderSize = targetSize * 3;
        int tw, th;
        if (sw >= sh) {
            tw = Math.min(renderSize, sw);
            th = (int) ((double) sh / sw * tw);
        } else {
            th = Math.min(renderSize, sh);
            tw = (int) ((double) sw / sh * th);
        }
        if (tw <= 0) tw = 1;
        if (th <= 0) th = 1;

        NativeImage result = new NativeImage(tw, th, false);

        // Area-averaging downscale: for each output pixel, average the source pixels
        double xRatio = (double) sw / tw;
        double yRatio = (double) sh / th;

        for (int y = 0; y < th; y++) {
            int sy0 = (int) (y * yRatio);
            int sy1 = Math.min(sh, (int) ((y + 1) * yRatio));
            if (sy1 <= sy0) sy1 = sy0 + 1;

            for (int x = 0; x < tw; x++) {
                int sx0 = (int) (x * xRatio);
                int sx1 = Math.min(sw, (int) ((x + 1) * xRatio));
                if (sx1 <= sx0) sx1 = sx0 + 1;

                long r = 0, g = 0, b = 0, a = 0;
                int count = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int pixel = src.getPixel(sx, sy);
                        a += (pixel >> 24) & 0xFF;
                        r += (pixel >> 16) & 0xFF;
                        g += (pixel >> 8) & 0xFF;
                        b += pixel & 0xFF;
                        count++;
                    }
                }
                if (count > 0) {
                    a /= count;
                    r /= count;
                    g /= count;
                    b /= count;
                }
                result.setPixel(x, y, (int) ((a << 24) | (r << 16) | (g << 8) | b));
            }
        }
        return result;
    }

    /**
     * Called every frame on the render thread. Checks if any background thumbnail loads
     * have completed, and uploads them to GPU textures.
     */
    public static void tick() {
        if (loading.isEmpty()) return;

        var iter = loading.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue().isDone()) {
                try {
                    NativeImage img = entry.getValue().get();
                    if (img != null) {
                        uploadTexture(entry.getKey(), img);
                    }
                } catch (Exception ignored) {
                }
                iter.remove();
            }
        }
    }

    /**
     * Uploads a NativeImage as a GPU texture. Must be called on the render thread.
     */
    private static void uploadTexture(Path file, NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        TextureManager tm = mc.getTextureManager();

        // Release existing texture if any
        Identifier existing = textures.get(file);
        if (existing != null) {
            tm.release(existing);
        }

        String id = "thumb_" + Integer.toHexString(file.toAbsolutePath().toString().hashCode());
        Identifier identifier = Identifier.fromNamespaceAndPath("prismshot", id);
        DynamicTexture tex = new DynamicTexture(() -> "Prismshot thumbnail", image);
        tm.register(identifier, tex);
        textures.put(file, identifier);
        dimensions.put(file, new int[]{image.getWidth(), image.getHeight()});
        accessOrder.put(file, System.nanoTime());
        evictIfNeeded();
    }

    /**
     * Returns the GPU texture identifier for a screenshot, or null if not yet loaded.
     */
    public static Identifier getTexture(Path file) {
        Identifier id = textures.get(file);
        if (id != null) {
            accessOrder.put(file, System.nanoTime());
        }
        return id;
    }

    /**
     * Returns the thumbnail dimensions [width, height] for a screenshot, or null if not loaded.
     */
    public static int[] getDimensions(Path file) {
        return dimensions.get(file);
    }

    /**
     * Evicts oldest textures if the cache exceeds the configured maximum count.
     * The maximum count is derived from the configured max cache MB and thumbnail size.
     */
    private static void evictIfNeeded() {
        int maxCount = Math.max(50, Config.GALLERY_MAX_CACHE_MB * 1024 * 1024 / (Config.GALLERY_THUMBNAIL_SIZE * Config.GALLERY_THUMBNAIL_SIZE * 4));
        while (accessOrder.size() > maxCount) {
            Path oldest = null;
            for (Map.Entry<Path, Long> e : accessOrder.entrySet()) {
                oldest = e.getKey();
                break;
            }
            if (oldest == null) break;
            accessOrder.remove(oldest);
            Identifier id = textures.remove(oldest);
            if (id != null) {
                Minecraft.getInstance().getTextureManager().release(id);
            }
        }
    }

    /**
     * Releases all GPU textures. Called when the gallery screen closes.
     */
    public static void releaseAll() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (Identifier id : textures.values()) {
            tm.release(id);
        }
        textures.clear();
        dimensions.clear();
        accessOrder.clear();
    }

    /**
     * Releases a single texture (e.g., when a screenshot is deleted).
     */
    public static void release(Path file) {
        Identifier id = textures.remove(file);
        if (id != null) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        dimensions.remove(file);
        accessOrder.remove(file);
        loading.remove(file);
    }
}
