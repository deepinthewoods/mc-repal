package ninja.trek.config;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ninja.trek.LayerInfo;
import ninja.trek.LayerManager;
import ninja.trek.Repal;
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
    private ButtonWidget moveToLayerButton;
    private ButtonWidget cycleLayerButton;
    private TextFieldWidget layerNameField;
    private List<LayerInfo> layers;
    private int selectedLayerIndex = 0;

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;
    private static final int FIELD_HEIGHT = 20;

    public LayerManagementUI(MinecraftClient client, int x, int y, int width) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.layers = LayerManager.getInstance().getAllLayers();
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

        // Move to layer and cycle layer buttons
        moveToLayerButton = ButtonWidget.builder(Text.translatable("repal.layer.move"), this::onMoveToLayerClick)
                .dimensions(x, currentY, halfWidth, BUTTON_HEIGHT)
                .build();

        cycleLayerButton = ButtonWidget.builder(Text.translatable("repal.layer.cycle"), this::onCycleLayerClick)
                .dimensions(x + halfWidth + BUTTON_SPACING, currentY, halfWidth, BUTTON_HEIGHT)
                .build();

        updateButtonStates();
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
        boolean hasSelectedTextures = !TextureManager.getSelectedTextures().isEmpty();
        boolean canDeleteLayer = hasLayers && layers.size() > 1;

        deleteLayerButton.active = canDeleteLayer;
        moveToLayerButton.active = hasSelectedTextures && hasLayers;
        cycleLayerButton.active = hasLayers;
        layerNameField.setEditable(hasLayers);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw layer name field
        layerNameField.render(context, mouseX, mouseY, delta);

        // Draw buttons
        newLayerButton.render(context, mouseX, mouseY, delta);
        deleteLayerButton.render(context, mouseX, mouseY, delta);
        moveToLayerButton.render(context, mouseX, mouseY, delta);
        cycleLayerButton.render(context, mouseX, mouseY, delta);

        // Draw layer info
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

    private void onNewLayerClick(ButtonWidget button) {
        String name = "Layer " + (layers.size() + 1);
        LayerInfo newLayer = LayerManager.getInstance().createLayer(name);
        layers = LayerManager.getInstance().getAllLayers();
        selectedLayerIndex = layers.indexOf(newLayer);
        updateLayerNameField();
        updateButtonStates();
    }

    private void onDeleteLayerClick(ButtonWidget button) {
        if (layers.size() <= 1) return;

        LayerInfo layerToDelete = layers.get(selectedLayerIndex);
        LayerManager.getInstance().deleteLayer(layerToDelete.getId());
        layers = LayerManager.getInstance().getAllLayers();
        selectedLayerIndex = Math.min(selectedLayerIndex, layers.size() - 1);
        updateLayerNameField();
        updateButtonStates();
    }

    private void onMoveToLayerClick(ButtonWidget button) {
        Set<Identifier> selectedTextures = TextureManager.getSelectedTextures();
        if (selectedTextures.isEmpty() || layers.isEmpty()) return;

        LayerInfo targetLayer = layers.get(selectedLayerIndex);
        TextureManager.moveSelectedTexturesToLayer(targetLayer);
        updateButtonStates();
    }

    private void onCycleLayerClick(ButtonWidget button) {
        if (layers.isEmpty()) return;

        selectedLayerIndex = (selectedLayerIndex + 1) % layers.size();
        LayerManager.getInstance().setActiveLayer(layers.get(selectedLayerIndex).getId());
        updateLayerNameField();
        updateButtonStates();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (layerNameField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (layerNameField.isFocused() && keyCode == 257) { // Enter key
            String newName = layerNameField.getText().trim();
            if (!newName.isEmpty() && !layers.isEmpty()) {
                LayerInfo currentLayer = layers.get(selectedLayerIndex);
                currentLayer.setName(newName);
                layerNameField.setFocused(false);
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
                moveToLayerButton,
                cycleLayerButton
        );
    }

    public void tick() {
        layerNameField.tick();
    }
}