package ninja.trek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ninja.trek.Repal;

import java.io.IOException;
import java.io.InputStream;

public class RepalClient implements ClientModInitializer {
	private static NativeImageBackedTexture previewTexture;
	public static final Identifier PREVIEW_TEXTURE_ID = Identifier.of(Repal.MOD_ID, "preview");

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			initializePreviewTexture(client);
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (previewTexture != null) {
				client.getTextureManager().destroyTexture(PREVIEW_TEXTURE_ID);
				previewTexture.close();
				previewTexture = null;
			}
		});
	}

	private void initializePreviewTexture(MinecraftClient client) {
		try {
			// Get the grass block texture resource
			var resource = client.getResourceManager()
					.getResource(Identifier.of("minecraft", "textures/block/grass_block_top.png"))
					.orElseThrow();

			// Read the texture directly as NativeImage
			try (InputStream stream = resource.getInputStream()) {
				NativeImage nativeImage = NativeImage.read(stream);
				previewTexture = new NativeImageBackedTexture(nativeImage);
				client.getTextureManager().registerTexture(PREVIEW_TEXTURE_ID, previewTexture);
			}
		} catch (Exception e) {
			Repal.LOGGER.error("Failed to initialize preview texture", e);
			e.printStackTrace();

			// Fallback to a solid color if loading fails
			try {
				NativeImage fallback = new NativeImage(16, 16, false);
				int grassColor = 0xFF7BAA3F; // Default grass green color
				for (int x = 0; x < 16; x++) {
					for (int y = 0; y < 16; y++) {
						fallback.setColor(x, y, grassColor);
					}
				}
				previewTexture = new NativeImageBackedTexture(fallback);
				client.getTextureManager().registerTexture(PREVIEW_TEXTURE_ID, previewTexture);
			} catch (Exception fallbackError) {
				Repal.LOGGER.error("Failed to create fallback texture", fallbackError);
			}
		}
	}

	/**
	 * Helper method to set a pixel color on a NativeImage.
	 *
	 * @param image The NativeImage instance.
	 * @param x The x coordinate.
	 * @param y The y coordinate.
	 * @param color The ARGB color value.
	 */
	private static void setColorArgb(NativeImage image, int x, int y, int color) {
		image.setColor(x, y, color);
	}

	public static void updatePreviewTexture(NativeImage newImage) {
		MinecraftClient client = MinecraftClient.getInstance();

		// Ensure we're on the main thread
		if (!client.isOnThread()) {
			client.execute(() -> updatePreviewTexture(newImage));
			return;
		}

		try {
			// Clean up old texture if it exists
			if (previewTexture != null) {
				previewTexture.close();
				client.getTextureManager().destroyTexture(PREVIEW_TEXTURE_ID);
			}

			// Create and register new texture
			previewTexture = new NativeImageBackedTexture(newImage);
			client.getTextureManager().registerTexture(PREVIEW_TEXTURE_ID, previewTexture);

		} catch (Exception e) {
			Repal.LOGGER.error("Failed to update preview texture", e);
			e.printStackTrace();
		}
	}
}