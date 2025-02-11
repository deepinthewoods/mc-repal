package ninja.trek.config;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ninja.trek.LayerInfo;
import ninja.trek.LayerManager;
import ninja.trek.Repal;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LayerImportExport {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path PRESETS_DIR = FabricLoader.getInstance()
            .getGameDir()
            .resolve("config")
            .resolve("repal")
            .resolve("presets");

    public static void exportLayers(String presetName) {
        try {
            Files.createDirectories(PRESETS_DIR);

            // Create export data structure
            JsonObject exportData = new JsonObject();
            exportData.addProperty("name", presetName);
            exportData.addProperty("timestamp",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Add layers data
            JsonArray layersArray = new JsonArray();
            for (LayerInfo layer : LayerManager.getInstance().getAllLayers()) {
                JsonObject layerObject = new JsonObject();
                layerObject.addProperty("name", layer.getName());
                layerObject.addProperty("contrast", layer.getContrast());
                layerObject.addProperty("saturation", layer.getSaturation());
                layerObject.addProperty("palette", layer.getPalette());

                // Add textures
                JsonArray texturesArray = new JsonArray();
                for (Identifier texture : layer.getTextures()) {
                    texturesArray.add(texture.toString());
                }
                layerObject.add("textures", texturesArray);

                layersArray.add(layerObject);
            }
            exportData.add("layers", layersArray);

            // Save to file
            String filename = presetName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
            Path exportPath = PRESETS_DIR.resolve(filename);
            Files.writeString(exportPath, GSON.toJson(exportData));

            Repal.LOGGER.info("Exported layer preset to: {}", exportPath);
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to export layers", e);
        }
    }

    public static List<String> getAvailablePresets() {
        List<String> presets = new ArrayList<>();
        try {
            Files.createDirectories(PRESETS_DIR);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(PRESETS_DIR, "*.json")) {
                for (Path path : stream) {
                    String filename = path.getFileName().toString();
                    presets.add(filename.substring(0, filename.lastIndexOf('.')));
                }
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to list presets", e);
        }
        return presets;
    }

    public static boolean importLayers(String presetName) {
        try {
            Path importPath = PRESETS_DIR.resolve(presetName + ".json");
            if (!Files.exists(importPath)) {
                Repal.LOGGER.error("Preset file not found: {}", importPath);
                return false;
            }

            String jsonContent = Files.readString(importPath);
            JsonObject importData = JsonParser.parseString(jsonContent).getAsJsonObject();

            LayerManager layerManager = LayerManager.getInstance();
            layerManager.reset(); // Clear existing layers

            JsonArray layersArray = importData.getAsJsonArray("layers");
            for (JsonElement layerElement : layersArray) {
                JsonObject layerObject = layerElement.getAsJsonObject();

                // Create new layer
                LayerInfo layer = layerManager.createLayer(
                        layerObject.get("name").getAsString()
                );

                // Set layer properties
                layer.setContrast(layerObject.get("contrast").getAsInt());
                layer.setSaturation(layerObject.get("saturation").getAsInt());
                layer.setPalette(layerObject.get("palette").getAsString());

                // Add textures
                JsonArray texturesArray = layerObject.getAsJsonArray("textures");
                for (JsonElement textureElement : texturesArray) {
                    layer.addTexture(Identifier.of(textureElement.getAsString()));
                }
            }

            return true;
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to import preset: {}", presetName, e);
            return false;
        }
    }
}

