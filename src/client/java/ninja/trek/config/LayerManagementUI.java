package ninja.trek.config;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ninja.trek.LayerInfo;
import ninja.trek.LayerManager;
import ninja.trek.TextureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

public class LayerManagementUI {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private ButtonWidget newLayerButton;
    private ButtonWidget deleteLayerButton;
    private ButtonWidget moveButton;
    private ButtonWidget destinationButton;
    private ButtonWidget cycleLayerButton;
    private TextFieldWidget layerNameField;
    private List<LayerInfo> layers;
    private int selectedLayerIndex = 0;
    private int destinationLayerIndex = 0;
    private TexturePreviewUI texturePreviewUI;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;
    private static final int FIELD_HEIGHT = 20;
    private RepalModMenu.MergedConfigScreen configScreen;

    public LayerManagementUI(MinecraftClient client, int x, int y, int width) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.layers = LayerManager.getInstance().getAllLayers();
    }

    public void setConfigScreen(RepalModMenu.MergedConfigScreen screen) {
        this.configScreen = screen;
    }

    public void setTexturePreviewUI(TexturePreviewUI texturePreviewUI) {
        this.texturePreviewUI = texturePreviewUI;
        if (texturePreviewUI != null && !layers.isEmpty()) {
            texturePreviewUI.setCurrentLayer(layers.get(selectedLayerIndex).getId());
        }
    }

    public void init() {
        int currentY = y;
        int halfWidth = (width - BUTTON_SPACING) / 2;

        // Layer name field
        layerNameField = new TextFieldWidget(
                client.textRenderer,
                x,
                currentY,
                width,
                FIELD_HEIGHT,
                Text.empty()
        );
        layerNameField.setMaxLength(32);
        updateLayerNameField();
        currentY += FIELD_HEIGHT + BUTTON_SPACING;

        // New and Delete layer buttons
        newLayerButton = ButtonWidget.builder(Text.translatable("repal.layer.new"), this::onNewLayerClick)
                .dimensions(x, currentY, halfWidth, BUTTON_HEIGHT)
                .build();
        deleteLayerButton = ButtonWidget.builder(Text.translatable("repal.layer.delete"), this::onDeleteLayerClick)
                .dimensions(x + halfWidth + BUTTON_SPACING, currentY, halfWidth, BUTTON_HEIGHT)
                .build();
        currentY += BUTTON_HEIGHT + BUTTON_SPACING;

        // Move controls and layer cycling (3 equal width buttons)
        int thirdWidth = (width - 2 * BUTTON_SPACING) / 3;
        moveButton = ButtonWidget.builder(Text.literal("Move"), this::onMoveClick)
                .dimensions(x, currentY, thirdWidth, BUTTON_HEIGHT)
                .build();

        destinationButton = ButtonWidget.builder(Text.literal("To: Default"), this::onDestinationClick)
                .dimensions(x + thirdWidth + BUTTON_SPACING, currentY, thirdWidth, BUTTON_HEIGHT)
                .build();

        cycleLayerButton = ButtonWidget.builder(Text.translatable("repal.layer.cycle"), this::onCycleLayerClick)
                .dimensions(x + 2 * (thirdWidth + BUTTON_SPACING), currentY, thirdWidth, BUTTON_HEIGHT)
                .build();

        updateButtonStates();
        updateDestinationButton();
    }

    private void updateLayerNameField() {
        if (!layers.isEmpty()) {
            LayerInfo currentLayer = layers.get(selectedLayerIndex);
            layerNameField.setText(currentLayer.getName());
            layerNameField.setPlaceholder(Text.translatable("repal.layer.name.placeholder"));
        }
    }

    private void updateButtonStates() {
        boolean hasLayers = !layers.isEmpty();
        boolean canDeleteLayer = hasLayers && layers.size() > 1;

        deleteLayerButton.active = canDeleteLayer;
        moveButton.active = true; // Move button is always enabled since invalid selections aren't possible
        destinationButton.active = hasLayers && layers.size() > 1;
        cycleLayerButton.active = hasLayers;
        layerNameField.setEditable(hasLayers);
    }

    private void updateDestinationButton() {
        if (!layers.isEmpty()) {
            LayerInfo destLayer = layers.get(destinationLayerIndex);
            destinationButton.setMessage(Text.literal("To: " + destLayer.getName()));
        }
    }

    private void onDestinationClick(ButtonWidget button) {
        if (layers.isEmpty() || layers.size() <= 1) return;

        do {
            destinationLayerIndex = (destinationLayerIndex + 1) % layers.size();
        } while (destinationLayerIndex == selectedLayerIndex);

        updateDestinationButton();
    }

    private void onMoveClick(ButtonWidget button) {
        if (layers.isEmpty()) return;

        Set<Identifier> selectedTextures = TextureManager.getSelectedTextures();
        if (!selectedTextures.isEmpty()) {
            LayerInfo targetLayer = layers.get(destinationLayerIndex);
            TextureManager.moveSelectedTexturesToLayer(targetLayer);

            // Update UI after move
            if (texturePreviewUI != null) {
                texturePreviewUI.updateTextureList();
            }
            updateButtonStates();
        }
    }

    private void onNewLayerClick(ButtonWidget button) {
        String name = "Layer " + (layers.size() + 1);
        LayerInfo newLayer = LayerManager.getInstance().createLayer(name);
        layers = LayerManager.getInstance().getAllLayers();

        // Stay on current layer, just update the destination to the new layer
        destinationLayerIndex = layers.indexOf(newLayer);

        updateButtonStates();
        updateDestinationButton();
    }

    private void onDeleteLayerClick(ButtonWidget button) {
        if (layers.size() <= 1) return;

        LayerInfo layerToDelete = layers.get(selectedLayerIndex);
        LayerManager.getInstance().deleteLayer(layerToDelete.getId());
        layers = LayerManager.getInstance().getAllLayers();
        selectedLayerIndex = Math.min(selectedLayerIndex, layers.size() - 1);

        // Reset destination layer after deletion
        destinationLayerIndex = 0;
        if (destinationLayerIndex == selectedLayerIndex && layers.size() > 1) {
            destinationLayerIndex = 1;
        }

        updateLayerNameField();
        updateButtonStates();
        updateDestinationButton();
        notifyLayerChange(layers.get(selectedLayerIndex));
    }

    private void onCycleLayerClick(ButtonWidget button) {
        if (layers.isEmpty()) return;

        selectedLayerIndex = (selectedLayerIndex + 1) % layers.size();
        LayerInfo newLayer = layers.get(selectedLayerIndex);

        // Clear selections when changing layers
        TextureManager.clearSelection();

        // Reset destination layer when changing current layer
        destinationLayerIndex = 0;
        if (destinationLayerIndex == selectedLayerIndex && layers.size() > 1) {
            destinationLayerIndex = 1;
        }

        updateLayerNameField();
        updateButtonStates();
        updateDestinationButton();
        notifyLayerChange(newLayer);
    }

    private void notifyLayerChange(LayerInfo newLayer) {
        if (texturePreviewUI != null && newLayer != null) {
            texturePreviewUI.setCurrentLayer(newLayer.getId());
        }
        LayerManager.getInstance().setActiveLayer(newLayer.getId());
        // Add this line:
        if (configScreen != null) {
            configScreen.updateSliderValues(newLayer);
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layerNameField.render(context, mouseX, mouseY, delta);
        newLayerButton.render(context, mouseX, mouseY, delta);
        deleteLayerButton.render(context, mouseX, mouseY, delta);
        moveButton.render(context, mouseX, mouseY, delta);
        destinationButton.render(context, mouseX, mouseY, delta);
        cycleLayerButton.render(context, mouseX, mouseY, delta);

        if (!layers.isEmpty()) {
            LayerInfo currentLayer = layers.get(selectedLayerIndex);
            String info = String.format("%d/%d: %s (%d textures)",
                    selectedLayerIndex + 1,
                    layers.size(),
                    currentLayer.getName(),
                    currentLayer.getTextures().size()
            );
            context.drawTextWithShadow(
                    client.textRenderer,
                    info,
                    x,
                    y + FIELD_HEIGHT * 3 + BUTTON_SPACING * 4,
                    0xFFFFFF
            );
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return layerNameField.mouseClicked(mouseX, mouseY, button);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (layerNameField.isFocused() && keyCode == 257) { // Enter key
            String newName = layerNameField.getText().trim();
            if (!newName.isEmpty() && !layers.isEmpty()) {
                LayerInfo currentLayer = layers.get(selectedLayerIndex);
                currentLayer.setName(newName);
                layerNameField.setFocused(false);
                updateDestinationButton(); // Update destination button in case it shows the renamed layer
                return true;
            }
        }
        return layerNameField.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char chr, int modifiers) {
        return layerNameField.charTyped(chr, modifiers);
    }

    public List<ButtonWidget> getButtons() {
        return Arrays.asList(
                newLayerButton,
                deleteLayerButton,
                moveButton,
                destinationButton,
                cycleLayerButton
        );
    }
}