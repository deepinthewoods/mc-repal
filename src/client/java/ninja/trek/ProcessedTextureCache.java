package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;


public class ProcessedTextureCache {
    private static class CacheKey {
        private final Identifier textureId;
        private final UUID layerId;
        private final int contrast;
        private final int saturation;
        private final int hue;  // Added hue to cache key
        private final String palette;

        public CacheKey(Identifier textureId, LayerInfo layer) {
            this.textureId = textureId;
            this.layerId = layer.getId();
            this.contrast = layer.getContrast();
            this.saturation = layer.getSaturation();
            this.hue = layer.getHue();  // Include hue in cache key
            this.palette = layer.getPalette();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return textureId.equals(cacheKey.textureId) &&
                    layerId.equals(cacheKey.layerId) &&
                    contrast == cacheKey.contrast &&
                    saturation == cacheKey.saturation &&
                    hue == cacheKey.hue &&  // Include hue in equals
                    Objects.equals(palette, cacheKey.palette);
        }

        @Override
        public int hashCode() {
            return Objects.hash(textureId, layerId, contrast, saturation, hue, palette);  // Include hue in hash
        }
    }

    private static final ConcurrentHashMap<CacheKey, Identifier> processedTextureIds = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100; // Increased due to multiple layers

    private static void processTexture(Identifier originalId, Identifier processedId, LayerInfo layer) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            var resource = client.getResourceManager()
                    .getResource(originalId)
                    .orElseThrow();

            try (InputStream stream = resource.getInputStream()) {
                BufferedImage originalImage = ImageIO.read(stream);
                if (originalImage == null) {
                    throw new IllegalStateException("Failed to read image: " + originalId);
                }

                List<Color> layerPalette = RepalResourceReloadListener.getLayerPaletteColors(layer);
                BufferedImage processedImage = ImageProcessor.processImage(
                        originalImage,
                        layerPalette,
                        layer.getContrast(),
                        layer.getSaturation(),
                        layer.getHue()  // Added hue parameter
                );

                // Convert to NativeImage
                NativeImage nativeImage = new NativeImage(
                        processedImage.getWidth(),
                        processedImage.getHeight(),
                        false
                );

                // Copy processed image data to NativeImage
                for (int y = 0; y < processedImage.getHeight(); y++) {
                    for (int x = 0; x < processedImage.getWidth(); x++) {
                        int argb = processedImage.getRGB(x, y);
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                        nativeImage.setColor(x, y, abgr);
                    }
                }

                // Register the texture
                client.execute(() -> {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
                    client.getTextureManager().registerTexture(processedId, texture);
                });
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to process texture: " + originalId + " for layer: " + layer.getName(), e);
        }
    }

    public static Identifier getProcessedTexture(Identifier originalTexture, LayerInfo layer) {
        CacheKey key = new CacheKey(originalTexture, layer);
        return processedTextureIds.computeIfAbsent(key, k -> {
            Identifier processedId = Identifier.of(Repal.MOD_ID,
                    "processed/" + layer.getId() + "/" +
                            originalTexture.getPath().replace('/', '_') +
                            "_" + layer.getPalette() +
                            "_" + layer.getContrast() +
                            "_" + layer.getSaturation() +
                            "_" + layer.getHue());  // Added hue to processed texture identifier
            processTexture(originalTexture, processedId, layer);
            return processedId;
        });
    }


    private static void cleanCacheIfNeeded() {
        if (processedTextureIds.size() > MAX_CACHE_SIZE) {
            MinecraftClient client = MinecraftClient.getInstance();
            processedTextureIds.forEach((key, processedId) -> {
                client.getTextureManager().destroyTexture(processedId);
            });
            processedTextureIds.clear();
        }
    }

    public static void clearCache() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            processedTextureIds.forEach((key, processedId) -> {
                client.getTextureManager().destroyTexture(processedId);
            });
            processedTextureIds.clear();
        } else {
            client.execute(() -> {
                processedTextureIds.forEach((key, processedId) -> {
                    client.getTextureManager().destroyTexture(processedId);
                });
                processedTextureIds.clear();
            });
        }
    }


    public static void clearTexture(Identifier textureId) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Use client.execute to ensure we're on the main thread
        client.execute(() -> {
            processedTextureIds.entrySet().removeIf(entry -> {
                if (entry.getKey().textureId.equals(textureId)) {
                    client.getTextureManager().destroyTexture(entry.getValue());
                    return true;
                }
                return false;
            });
        });
    }


    public static void clearLayerCache(UUID layerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        processedTextureIds.entrySet().removeIf(entry -> {
            if (entry.getKey().layerId.equals(layerId)) {
                client.getTextureManager().destroyTexture(entry.getValue());
                return true;
            }
            return false;
        });
    }
}