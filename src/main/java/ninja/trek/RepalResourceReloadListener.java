package ninja.trek;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.awt.Color;

public class RepalResourceReloadListener implements SimpleSynchronousResourceReloadListener {
    private static List<Color> palette1Colors = new ArrayList<>();
    private static List<Color> palette2Colors = new ArrayList<>();



    @Override
    public void reload(ResourceManager manager) {
        // Load both palettes
        loadPalette(manager, Repal.PALETTE_1, palette1Colors);
        loadPalette(manager, Repal.PALETTE_2, palette2Colors);
    }

    private void loadPalette(ResourceManager manager, Identifier paletteId, List<Color> colorList) {
        colorList.clear();
        try {
            manager.getResource(paletteId).ifPresent(resource -> {
                try (InputStream stream = resource.getInputStream()) {
                    BufferedImage image = ImageIO.read(stream);
                    Set<Color> uniqueColors = new HashSet<>();

                    // Scan every pixel in the image
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            Color color = new Color(image.getRGB(x, y), true);
                            // Only add fully opaque colors or fully transparent
                            if (color.getAlpha() == 255 || color.getAlpha() == 0) {
                                uniqueColors.add(color);
                            }
                        }
                    }

                    colorList.addAll(uniqueColors);
                    Repal.LOGGER.info("Loaded {} colors from palette {}", uniqueColors.size(), paletteId);
                } catch (Exception e) {
                    Repal.LOGGER.error("Failed to load palette {}: {}", paletteId, e.getMessage());
                }
            });
        } catch (Exception e) {
            Repal.LOGGER.error("Error accessing palette {}: {}", paletteId, e.getMessage());
        }
    }

    // Getters for the palette colors
    public static List<Color> getPalette1Colors() {
        return new ArrayList<>(palette1Colors);
    }

    public static List<Color> getPalette2Colors() {
        return new ArrayList<>(palette2Colors);
    }

    // Get currently selected palette colors
    public static List<Color> getCurrentPaletteColors() {
        return Repal.getSelectedPalette() == 1 ? getPalette1Colors() : getPalette2Colors();
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of(Repal.MOD_ID, "textures");
    }
}