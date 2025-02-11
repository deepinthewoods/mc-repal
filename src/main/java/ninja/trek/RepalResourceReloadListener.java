package ninja.trek;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class RepalResourceReloadListener implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, List<Color>> paletteColors = new HashMap<>();
    private static final List<PaletteInfo> availablePalettes = new ArrayList<>();
    private static final Identifier PALETTE_1 = Identifier.of("repal", "textures/palette/pal1.png");
    private static final Identifier PALETTE_2 = Identifier.of("repal", "textures/palette/pal2.png");

    @Override
    public Identifier getFabricId() {
        return Identifier.of("repal", "palette_loader");
    }

    private void loadPalette(ResourceManager manager, Identifier paletteId, String paletteName) {
        try {
            manager.getResource(paletteId).ifPresent(resource -> {
                try (InputStream stream = resource.getInputStream()) {
                    BufferedImage image = ImageIO.read(stream);
                    Set<Color> uniqueColors = new HashSet<>();
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            Color color = new Color(image.getRGB(x, y), true);
                            if (color.getAlpha() == 255) {
                                uniqueColors.add(color);
                            }
                        }
                    }
                    List<Color> colors = new ArrayList<>(uniqueColors);
                    paletteColors.put(paletteName, colors);
                    availablePalettes.add(new PaletteInfo(paletteName, null, true));
                    Repal.LOGGER.info("Loaded {} colors from palette {}", uniqueColors.size(), paletteId);
                } catch (Exception e) {
                    Repal.LOGGER.error("Failed to load palette {}", paletteId, e);
                }
            });
        } catch (Exception e) {
            Repal.LOGGER.error("Error accessing palette {}", paletteId, e);
        }
    }

    private void loadCustomPalettes() {
        try {
            Path resourcePacksDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
            if (!Files.exists(resourcePacksDir)) {
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resourcePacksDir, "*.png")) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    name = name.substring(0, name.lastIndexOf('.'));
                    loadCustomPalette(path, name);
                }
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to load custom palettes", e);
        }
    }

    private void loadCustomPalette(Path path, String name) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            Set<Color> uniqueColors = new HashSet<>();

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    Color color = new Color(image.getRGB(x, y), true);
                    if (color.getAlpha() == 255) {
                        uniqueColors.add(color);
                    }
                }
            }

            List<Color> colors = new ArrayList<>(uniqueColors);
            paletteColors.put(name, colors);
            availablePalettes.add(new PaletteInfo(name, path, false));

            Repal.LOGGER.info("Loaded custom palette {} with {} colors", name, colors.size());
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to load custom palette: {}", name, e);
        }
    }

    public static List<PaletteInfo> getAvailablePalettes() {
        return new ArrayList<>(availablePalettes);
    }

    public static List<Color> getPaletteColors(String paletteName) {
        return paletteColors.getOrDefault(paletteName, Collections.emptyList());
    }

    public static List<Color> getLayerPaletteColors(LayerInfo layer) {
        if (layer == null) {
            Repal.LOGGER.warn("Attempted to get palette colors for null layer");
            return Collections.emptyList();
        }
        String paletteName = layer.getPalette();
        List<Color> colors = paletteColors.get(paletteName);

        if (colors == null || colors.isEmpty()) {
            Repal.LOGGER.warn("No colors found for palette '{}' in layer '{}'", paletteName, layer.getName());
            // Fall back to default palette
            colors = paletteColors.getOrDefault("builtin_1", Collections.emptyList());
        }

        return colors;
    }

    @Override
    public void reload(ResourceManager manager) {
        paletteColors.clear();
        availablePalettes.clear();

        // Load built-in palettes
        loadPalette(manager, PALETTE_1, "builtin_1");
        loadPalette(manager, PALETTE_2, "builtin_2");

        // Load custom palettes
        loadCustomPalettes();

        // Validate all layer palettes after reload
        LayerManager layerManager = LayerManager.getInstance();
        for (LayerInfo layer : layerManager.getAllLayers()) {
            String currentPalette = layer.getPalette();
            if (!paletteColors.containsKey(currentPalette)) {
                Repal.LOGGER.warn("Invalid palette '{}' in layer '{}', resetting to default",
                        currentPalette, layer.getName());
                layer.setPalette("builtin_1");
            }
        }

        // Log loaded palettes
        Repal.LOGGER.info("Loaded {} palettes: {}",
                paletteColors.size(),
                String.join(", ", paletteColors.keySet()));
    }
}