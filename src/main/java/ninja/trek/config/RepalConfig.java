package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ninja.trek.PaletteInfo;
import ninja.trek.Repal;
import ninja.trek.RepalResourceReloadListener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class RepalConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("repal.json");

    private static RepalConfig INSTANCE;

    private int preContrast = 0;
    private int preSaturation = 0;
    private String packName = "repal";

    private String selectedPalette = "builtin_1";


    public void setSelectedPalette(String name) {
        List<PaletteInfo> availablePalettes = RepalResourceReloadListener.getAvailablePalettes();
        boolean isValidPalette = availablePalettes.stream()
                .anyMatch(p -> p.getName().equals(name));

        if (isValidPalette) {
            this.selectedPalette = name;
            // Actually set the current palette in the RepalResourceReloadListener
            RepalResourceReloadListener.setCurrentPalette(name);
            Repal.LOGGER.info("Set and activated selected palette: {}", name);
        } else {
            Repal.LOGGER.warn("Attempted to set invalid palette name: {}. Available: {}",
                    name, availablePalettes.stream()
                            .map(PaletteInfo::getName)
                            .collect(Collectors.joining(", ")));
        }
        save();
    }

    public String selectedPalette() {
        // Validate current selection
        List<PaletteInfo> availablePalettes = RepalResourceReloadListener.getAvailablePalettes();
        boolean isValidPalette = availablePalettes.stream()
                .anyMatch(p -> p.getName().equals(selectedPalette));

        if (!isValidPalette) {
            Repal.LOGGER.warn("Current palette {} is not valid. Defaulting to builtin_1", selectedPalette);
            selectedPalette = "builtin_1";
            save();
        }
        return selectedPalette;
    }

    // Private constructor to enforce singleton
    private RepalConfig() {}

    public static RepalConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(reader, RepalConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new RepalConfig();
                }
            } catch (Exception e) {
                Repal.LOGGER.error("Failed to load config: ", e);
                INSTANCE = new RepalConfig();
            }
        } else {
            INSTANCE = new RepalConfig();
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(get(), writer);
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to save config: ", e);
        }
    }

    // Getters
    public int preContrast() {
        return preContrast;
    }

    public int preSaturation() {
        return preSaturation;
    }



    public String packName() {
        return packName;
    }

    // Setters with validation
    public void setPreContrast(int value) {
        this.preContrast = Math.min(Math.max(value, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
        save();
    }

    public void setPreSaturation(int value) {
        Repal.LOGGER.info("set sat");

        this.preSaturation = Math.min(Math.max(value, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
        save();
    }



    public void setPackName(String name) {
        this.packName = name == null || name.trim().isEmpty() ? "repal" : name.trim();
        save();
    }
}