package ninja.trek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ninja.trek.config.RepalConfig;

import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class ProcessedTextureCache {
    private static final ConcurrentHashMap<Identifier, Identifier> processedTextureIds = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 50;

    public static Identifier getProcessedTexture(Identifier originalTexture) {
        return processedTextureIds.computeIfAbsent(originalTexture, id -> {
            Identifier processedId = Identifier.of(Repal.MOD_ID, "processed/" + id.getPath().replace('/', '_'));
            processTexture(id, processedId);
            cleanCacheIfNeeded();
            return processedId;
        });
    }

    private static void processTexture(Identifier originalId, Identifier processedId) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            // Get the original texture resource
            var resource = client.getResourceManager()
                    .getResource(originalId)
                    .orElseThrow();

            // Read and process the image
            try (InputStream stream = resource.getInputStream()) {
                BufferedImage originalImage = ImageIO.read(stream);
                BufferedImage processedImage = ImageProcessor.processImage(
                        originalImage,
                        RepalResourceReloadListener.getCurrentPaletteColors(),
                        RepalConfig.get().preContrast(),
                        RepalConfig.get().preSaturation()
                );

                // Convert to NativeImage and register as texture
                NativeImage nativeImage = new NativeImage(
                        processedImage.getWidth(),
                        processedImage.getHeight(),
                        false
                );

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

                client.execute(() -> {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
                    client.getTextureManager().registerTexture(processedId, texture);
                });
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to process texture: " + originalId, e);
        }
    }

    private static void cleanCacheIfNeeded() {
        if (processedTextureIds.size() > MAX_CACHE_SIZE) {
            MinecraftClient client = MinecraftClient.getInstance();
            processedTextureIds.forEach((original, processed) -> {
                client.getTextureManager().destroyTexture(processed);
            });
            processedTextureIds.clear();
        }
    }

    public static void clearCache() {
        MinecraftClient client = MinecraftClient.getInstance();
        processedTextureIds.forEach((original, processed) -> {
            client.getTextureManager().destroyTexture(processed);
        });
        processedTextureIds.clear();
    }
}