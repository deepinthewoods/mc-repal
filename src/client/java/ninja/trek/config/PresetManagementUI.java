package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ninja.trek.config.LayerImportExport;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (presetNameField != null && presetNameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (presetNameField != null && presetNameField.charTyped(chr, modifiers)) {
            return true;
        }
        return false;
    }

    private Optional<TextFieldWidget> layerNameField = Optional.empty();

    public void tick() {
        layerNameField.ifPresent(TextFieldWidget::tick);
    }
}