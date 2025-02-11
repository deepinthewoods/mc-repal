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

public class PresetManagementUI {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private ButtonWidget importButton;
    private ButtonWidget exportButton;
    private TextFieldWidget presetNameField;
    private List<String> availablePresets;
    private int selectedPresetIndex = -1;

    private static final int BUTTON_HEIGHT = 20;
    private static final int FIELD_HEIGHT = 20;
    private static final int SPACING = 4;
    private static final int LIST_HEIGHT = 100;

    public PresetManagementUI(MinecraftClient client, int x, int y, int width) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.availablePresets = LayerImportExport.getAvailablePresets();
    }

    public void init() {
        int currentY = y;
        int halfWidth = (width - SPACING) / 2;

        presetNameField = new TextFieldWidget(
                client.textRenderer,
                x,
                currentY,
                width,
                FIELD_HEIGHT,
                Text.empty()
        );
        presetNameField.setMaxLength(32);
        presetNameField.setPlaceholder(Text.translatable("repal.preset.name.placeholder"));

        currentY += FIELD_HEIGHT + SPACING;

        importButton = ButtonWidget.builder(Text.translatable("repal.preset.import"), this::onImportClick)
                .dimensions(x, currentY, halfWidth, BUTTON_HEIGHT)
                .build();

        exportButton = ButtonWidget.builder(Text.translatable("repal.preset.export"), this::onExportClick)
                .dimensions(x + halfWidth + SPACING, currentY, halfWidth, BUTTON_HEIGHT)
                .build();

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasValidName = !presetNameField.getText().trim().isEmpty();
        boolean hasSelection = selectedPresetIndex >= 0;

        importButton.active = hasSelection;
        exportButton.active = hasValidName;
    }

    private void onImportClick(ButtonWidget button) {
        if (selectedPresetIndex >= 0 && selectedPresetIndex < availablePresets.size()) {
            String selectedPreset = availablePresets.get(selectedPresetIndex);
            if (LayerImportExport.importLayers(selectedPreset)) {
                // Success notification would go here
            }
        }
    }

    private void onExportClick(ButtonWidget button) {
        String presetName = presetNameField.getText().trim();
        if (!presetName.isEmpty()) {
            LayerImportExport.exportLayers(presetName);
            availablePresets = LayerImportExport.getAvailablePresets();
            // Success notification would go here
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw preset name field
        presetNameField.render(context, mouseX, mouseY, delta);

        // Draw buttons
        importButton.render(context, mouseX, mouseY, delta);
        exportButton.render(context, mouseX, mouseY, delta);

        // Draw available presets list
        int listY = y + FIELD_HEIGHT + BUTTON_HEIGHT + SPACING * 2;
        context.fill(x, listY, x + width, listY + LIST_HEIGHT, 0x80000000);

        int entryHeight = 20;
        int scrollOffset = 0;
        for (int i = 0; i < availablePresets.size(); i++) {
            int entryY = listY + i * entryHeight - scrollOffset;
            if (entryY >= listY && entryY + entryHeight <= listY + LIST_HEIGHT) {
                boolean isSelected = i == selectedPresetIndex;
                if (isSelected) {
                    context.fill(x, entryY, x + width, entryY + entryHeight, 0x80808080);
                }
                context.drawTextWithShadow(
                        client.textRenderer,
                        availablePresets.get(i),
                        x + 4,
                        entryY + 6,
                        isSelected ? 0xFFFFFF : 0xAAAAAA
                );
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (presetNameField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Handle preset list clicks
        int listY = y + FIELD_HEIGHT + BUTTON_HEIGHT + SPACING * 2;
        if (mouseX >= x && mouseX < x + width &&
                mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
            int entryHeight = 20;
            int clickedIndex = (int)((mouseY - listY) / entryHeight);
            if (clickedIndex >= 0 && clickedIndex < availablePresets.size()) {
                selectedPresetIndex = clickedIndex;
                updateButtonStates();
                return true;
            }
        }

        return false;
    }

    public List<ButtonWidget> getButtons() {
        return Arrays.asList(importButton, exportButton);
    }

    public void tick() {
        presetNameField.tick();
    }
}