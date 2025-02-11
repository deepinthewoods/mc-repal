package ninja.trek;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import ninja.trek.config.RepalConfig;

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
    private static String currentPalette = "builtin_1";
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
                            if (color.getAlpha() == 255) { // Only add fully opaque colors
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
            Repal.LOGGER.info("Scanning for palettes in: {}", resourcePacksDir);

            if (!Files.exists(resourcePacksDir)) {
                Repal.LOGGER.warn("Resource packs directory does not exist: {}", resourcePacksDir);
                return;
            }

            // Log all files in the directory for debugging
            try (DirectoryStream<Path> allFiles = Files.newDirectoryStream(resourcePacksDir)) {
                Repal.LOGGER.info("Files in resourcepacks directory:");
                for (Path path : allFiles) {
                    Repal.LOGGER.info(" - {}", path.getFileName());
                }
            }

            // Now scan for PNGs
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resourcePacksDir, "*.png")) {
                boolean foundAny = false;
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    name = name.substring(0, name.lastIndexOf('.'));
                    Repal.LOGGER.info("Found palette file: {}", path);
                    loadCustomPalette(path, name);
                    foundAny = true;
                }

                if (!foundAny) {
                    Repal.LOGGER.warn("No PNG files found in resourcepacks directory");
                }
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to load custom palettes", e);
            e.printStackTrace(); // Add full stack trace for debugging
        }
    }




    public static List<PaletteInfo> getAvailablePalettes() {
        return new ArrayList<>(availablePalettes);
    }


    private void loadCustomPalette(Path path, String name) {
        try {
            Repal.LOGGER.info("Starting to load custom palette: {} from {}", name, path);

            if (!Files.exists(path)) {
                Repal.LOGGER.error("Palette file does not exist: {}", path);
                return;
            }

            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                Repal.LOGGER.error("Failed to read image from {}", path);
                return;
            }

            Set<Color> uniqueColors = new HashSet<>();
            int totalPixels = 0;
            int opaquePixels = 0;

            // Process image and log detailed stats
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    totalPixels++;
                    Color color = new Color(image.getRGB(x, y), true);
                    if (color.getAlpha() == 255) {
                        uniqueColors.add(color);
                        opaquePixels++;
                    }
                }
            }

            List<Color> colors = new ArrayList<>(uniqueColors);

            // Log detailed information about the palette
            Repal.LOGGER.info("Palette statistics for {}", name);
            Repal.LOGGER.info("- Total pixels: {}", totalPixels);
            Repal.LOGGER.info("- Opaque pixels: {}", opaquePixels);
            Repal.LOGGER.info("- Unique colors: {}", colors.size());

            // Log some sample colors if any were found
            if (!colors.isEmpty()) {
                Repal.LOGGER.info("- Sample colors (first 5):");
                colors.stream().limit(5).forEach(color ->
                        Repal.LOGGER.info("  RGB({}, {}, {})",
                                color.getRed(), color.getGreen(), color.getBlue())
                );
            } else {
                Repal.LOGGER.warn("No valid colors found in palette!");
            }

            // Add to palette collections
            paletteColors.put(name, colors);
            availablePalettes.add(new PaletteInfo(name, path, false));

            // Verify the palette was added correctly
            if (paletteColors.containsKey(name)) {
                List<Color> storedColors = paletteColors.get(name);
                Repal.LOGGER.info("Palette {} successfully stored with {} colors",
                        name, storedColors.size());
            } else {
                Repal.LOGGER.error("Failed to store palette {} in paletteColors map!", name);
            }

        } catch (Exception e) {
            Repal.LOGGER.error("Failed to load custom palette: {} - {}", name, e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<Color> getCurrentPaletteColors() {
        List<Color> colors = paletteColors.getOrDefault(currentPalette, new ArrayList<>());
        Repal.LOGGER.info("Getting colors for palette '{}': found {} colors",
                currentPalette, colors.size());

        if (colors.isEmpty()) {
            Repal.LOGGER.warn("No colors found for current palette '{}'", currentPalette);
            Repal.LOGGER.info("Available palettes: {}",
                    String.join(", ", paletteColors.keySet()));
        }

        return colors;
    }

    public static void setCurrentPalette(String paletteName) {
        Repal.LOGGER.info("Attempting to set current palette to: {}", paletteName);

        if (paletteColors.containsKey(paletteName)) {
            currentPalette = paletteName;  // This is the key line
            int colorCount = paletteColors.get(paletteName).size();
            Repal.LOGGER.info("Successfully set current palette to {} ({} colors)",
                    paletteName, colorCount);
            ImageProcessor.clearCache();
        } else {
            Repal.LOGGER.error("Attempted to set non-existent palette: {}. Available palettes: {}",
                    paletteName, String.join(", ", paletteColors.keySet()));
        }
    }

    @Override
    public void reload(ResourceManager manager) {
        paletteColors.clear();
        availablePalettes.clear();

        Repal.LOGGER.info("Starting palette reload...");

        // Load built-in palettes first
        loadPalette(manager, PALETTE_1, "pal1");
        loadPalette(manager, PALETTE_2, "pal2");

        // Load custom palettes
        loadCustomPalettes();

        // After loading all palettes, ensure we have a valid current palette
        if (!paletteColors.containsKey(currentPalette)) {
            // Try to use the config's selected palette
            String configPalette = RepalConfig.get().selectedPalette();
            if (paletteColors.containsKey(configPalette)) {
                currentPalette = configPalette;
            } else {
                // Fall back to first available palette
                currentPalette = paletteColors.keySet().stream()
                        .findFirst()
                        .orElse("pal1");
            }
            Repal.LOGGER.info("Reset current palette to: {}", currentPalette);
        }

        // Log loaded palettes
        Repal.LOGGER.info("Loaded {} palettes: {}",
                paletteColors.size(),
                String.join(", ", paletteColors.keySet()));
        Repal.LOGGER.info("Current palette is: {} with {} colors",
                currentPalette,
                paletteColors.getOrDefault(currentPalette, Collections.emptyList()).size());
    }
}