package ninja.trek;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Repal implements ModInitializer {
	public static final String MOD_ID = "repal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Identifiers for our palette resources
	public static final Identifier PALETTE_1 = Identifier.of(MOD_ID, "textures/palette/pal1.png");
	public static final Identifier PALETTE_2 = Identifier.of(MOD_ID, "textures/palette/pal2.png");

	// Constants for adjustment ranges
	public static final int MIN_ADJUSTMENT = -100;
	public static final int MAX_ADJUSTMENT = 100;

	private static int selectedPalette = 1;
	private static int preContrast = 0;
	private static int preSaturation = 0;
	private static String packName = "repal";

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Repal");

		// Register the resource reload listener
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
				.registerReloadListener(new RepalResourceReloadListener());
	}

	// Getter methods
	public static int getSelectedPalette() {
		return selectedPalette;
	}

	public static int getPreContrast() {
		return preContrast;
	}

	public static int getPreSaturation() {
		return preSaturation;
	}

	// Setter methods with validation
	public static void setSelectedPalette(int palette) {
		selectedPalette = (palette == 1 || palette == 2) ? palette : 1;
	}

	public static void setPreContrast(int contrast) {
		preContrast = Math.max(MIN_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, contrast));
	}

	public static void setPreSaturation(int saturation) {
		preSaturation = Math.max(MIN_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, saturation));
	}

	public static String getPackName() {
		return packName;
	}

	public static void setPackName(String name) {
		packName = (name == null || name.trim().isEmpty()) ? "repal" : name.trim();
	}
}