package ninja.trek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ninja.trek.LayerManager;
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

    // Legacy fields (maintained for backward compatibility)
    private int preContrast = 0;
    private int preSaturation = 0;
    private String packName = "repal";
    private String selectedPalette = "builtin_1";

    // New field for layer data
    private String layerData;

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

                // Import layer data if it exists
                if (INSTANCE.layerData != null && !INSTANCE.layerData.isEmpty()) {
                    LayerManager.getInstance().importFromJson(INSTANCE.layerData);
                }

                // Migrate legacy settings to default layer if needed
                if (INSTANCE.layerData == null || INSTANCE.layerData.isEmpty()) {
                    var defaultLayer = LayerManager.getInstance().getActiveLayer();
                    defaultLayer.setContrast(INSTANCE.preContrast);
                    defaultLayer.setSaturation(INSTANCE.preSaturation);
                    defaultLayer.setPalette(INSTANCE.selectedPalette);
                    save(); // Save the migrated data
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

            // Update layer data before saving
            if (INSTANCE != null) {
                INSTANCE.layerData = LayerManager.getInstance().exportToJson();
            }

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(get(), writer);
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to save config: ", e);
        }
    }

    // Legacy getters (now pull from active layer)
    public int preContrast() {
        return LayerManager.getInstance().getActiveLayer().getContrast();
    }

    public int preSaturation() {
        return LayerManager.getInstance().getActiveLayer().getSaturation();
    }

    public String selectedPalette() {
        return LayerManager.getInstance().getActiveLayer().getPalette();
    }

    public String packName() {
        return packName;
    }

    // Legacy setters (now update active layer)
    public void setPreContrast(int value) {
        LayerManager.getInstance().getActiveLayer().setContrast(value);
        save();
    }

    public void setPreSaturation(int value) {
        LayerManager.getInstance().getActiveLayer().setSaturation(value);
        save();
    }

    public void setSelectedPalette(String name) {
        LayerManager.getInstance().getActiveLayer().setPalette(name);
        save();
    }

    public void setPackName(String name) {
        this.packName = name == null || name.trim().isEmpty() ? "repal" : name.trim();
        save();
    }
}