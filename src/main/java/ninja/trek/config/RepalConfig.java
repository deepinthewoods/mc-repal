package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ninja.trek.Repal;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

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
    private int selectedPalette = 1;
    private String packName = "repal";

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

    public int selectedPalette() {
        return selectedPalette;
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
        this.preSaturation = Math.min(Math.max(value, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
        save();
    }

    public void setSelectedPalette(int value) {
        this.selectedPalette = (value == 1 || value == 2) ? value : 1;
        save();
    }

    public void setPackName(String name) {
        this.packName = name == null || name.trim().isEmpty() ? "repal" : name.trim();
        save();
    }
}