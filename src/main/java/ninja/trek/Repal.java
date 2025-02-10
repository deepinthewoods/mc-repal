package ninja.trek;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ninja.trek.config.RepalConfig;

public class Repal implements ModInitializer {
	public static final String MOD_ID = "repal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Identifiers for our palette resources
	public static final Identifier PALETTE_1 = Identifier.of(MOD_ID, "textures/palette/pal1.png");
	public static final Identifier PALETTE_2 = Identifier.of(MOD_ID, "textures/palette/pal2.png");

	// Settings constants
	public static final int MIN_ADJUSTMENT = -100;
	public static final int MAX_ADJUSTMENT = 100;
	public static final int DEFAULT_ADJUSTMENT = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Repal - Texture Recoloring Mod");

		// Load config
		RepalConfig.load();

		// Register resource reload listener
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
				.registerReloadListener(new RepalResourceReloadListener());
	}

	// Static access methods that use the config
	public static int getPreContrast() {
		return RepalConfig.get().preContrast();
	}

	public static int getPreSaturation() {
		return RepalConfig.get().preSaturation();
	}

	public static int getSelectedPalette() {
		return RepalConfig.get().selectedPalette();
	}

	public static String getPackName() {
		return RepalConfig.get().packName();
	}

	public static void setPreContrast(int value) {
		RepalConfig.get().setPreContrast(value);
	}

	public static void setPreSaturation(int value) {
		RepalConfig.get().setPreSaturation(value);
	}

	public static void setSelectedPalette(int value) {
		RepalConfig.get().setSelectedPalette(value);
	}

	public static void setPackName(String name) {
		RepalConfig.get().setPackName(name);
	}
}