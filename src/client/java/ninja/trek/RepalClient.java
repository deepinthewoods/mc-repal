package ninja.trek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ninja.trek.Repal;

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
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, true);
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					int color = (x + y) % 2 == 0 ? 0xFF808080 : 0xFFCCCCCC;
					setColorArgb(image, x, y, color);
				}
			}
			previewTexture = new NativeImageBackedTexture(image);
			client.getTextureManager().registerTexture(PREVIEW_TEXTURE_ID, previewTexture);
		} catch (Exception e) {
			Repal.LOGGER.error("Failed to initialize preview texture", e);
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
		if (previewTexture != null) {
			client.getTextureManager().destroyTexture(PREVIEW_TEXTURE_ID);
			previewTexture.close();
		}
		previewTexture = new NativeImageBackedTexture(newImage);
		client.getTextureManager().registerTexture(PREVIEW_TEXTURE_ID, previewTexture);
	}
}