package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;


public class ProcessedTextureCache {
    private static class CacheKey {
        private final Identifier textureId;
        private final UUID layerId;

        public CacheKey(Identifier textureId, UUID layerId) {
            this.textureId = textureId;
            this.layerId = layerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return textureId.equals(cacheKey.textureId) && layerId.equals(cacheKey.layerId);
        }

        @Override
        public int hashCode() {
            return 31 * textureId.hashCode() + layerId.hashCode();
        }
    }

    private static final ConcurrentHashMap<CacheKey, Identifier> processedTextureIds = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100; // Increased due to multiple layers

    public static Identifier getProcessedTexture(Identifier originalTexture, LayerInfo layer) {
        if (layer == null) {
            Repal.LOGGER.error("Attempted to get processed texture with null layer");
            return originalTexture;
        }

        CacheKey key = new CacheKey(originalTexture, layer.getId());
        return processedTextureIds.computeIfAbsent(key, k -> {
            Identifier processedId = Identifier.of(Repal.MOD_ID,
                    "processed/" + layer.getId() + "/" + k.textureId.getPath().replace('/', '_'));
            processTexture(k.textureId, processedId, layer);
            cleanCacheIfNeeded();
            return processedId;
        });
    }

    private static void processTexture(Identifier originalId, Identifier processedId, LayerInfo layer) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            // Get the original texture resource
            var resource = client.getResourceManager()
                    .getResource(originalId)
                    .orElseThrow();

            // Read and process the image
            try (InputStream stream = resource.getInputStream()) {
                BufferedImage originalImage = ImageIO.read(stream);
                if (originalImage == null) {
                    throw new IllegalStateException("Failed to read image: " + originalId);
                }

                // Get layer-specific settings
                List<Color> layerPalette = RepalResourceReloadListener.getLayerPaletteColors(layer);

                BufferedImage processedImage = ImageProcessor.processImage(
                        originalImage,
                        layerPalette,
                        layer.getContrast(),
                        layer.getSaturation()
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

            } catch (Exception e) {
                Repal.LOGGER.error("Failed to process texture data for " + originalId, e);
                throw e;
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to process texture: " + originalId + " for layer: " + layer.getName(), e);
        }
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
        processedTextureIds.forEach((key, processedId) -> {
            client.getTextureManager().destroyTexture(processedId);
        });
        processedTextureIds.clear();
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