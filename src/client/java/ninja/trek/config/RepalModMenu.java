package ninja.trek.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.text.Text;
import ninja.trek.LayerInfo;
import ninja.trek.LayerManager;
import ninja.trek.TextureProcessor;

public class RepalModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new MergedConfigScreen(parent);
    }

    public class MergedConfigScreen extends Screen {
        private final Screen parent;
        private TextureComboBox textureSearch;
        private LayerManagementUI layerUI;
        private ProcessingParameterUI processingUI;
        private ButtonWidget processButton;
        private TexturePreviewUI texturePreviewUI;

        // Layout constants
        private static final int SIDE_PANEL_WIDTH = 200;
        private static final int UI_SPACING = 10;
        private static final int LAYER_UI_HEIGHT = 160;
        private static final int PROCESSING_UI_HEIGHT = 120;
        private static final int PRESET_UI_Y_OFFSET = 320; // LAYER_UI_HEIGHT + PROCESSING_UI_HEIGHT + spacing

        public MergedConfigScreen(Screen parent) {
            super(Text.translatable("repal.config.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int mainWidth = width - SIDE_PANEL_WIDTH - UI_SPACING;

            // Initialize texture preview UI first
            int previewsStartY = UI_SPACING * 3 + 20; // Space for search box
            int previewAreaX = 10;
            int previewAreaWidth = mainWidth - 20;
            int previewAreaHeight = height - previewsStartY - 20;
            texturePreviewUI = new TexturePreviewUI(this, client, previewAreaX, previewsStartY, previewAreaWidth, previewAreaHeight);
            texturePreviewUI.init();

            // Initialize layer management UI on the right side
            layerUI = new LayerManagementUI(client, width - SIDE_PANEL_WIDTH, 0, SIDE_PANEL_WIDTH);
            layerUI.init();
            layerUI.setTexturePreviewUI(texturePreviewUI);
            layerUI.setConfigScreen(this);

            // Initialize processing parameter UI below layer UI
            processingUI = new ProcessingParameterUI(this, client, width - SIDE_PANEL_WIDTH, LAYER_UI_HEIGHT + UI_SPACING, SIDE_PANEL_WIDTH);
            processingUI.init();


            // Initialize texture search centered at the top
            textureSearch = new TextureComboBox(
                    client,
                    mainWidth / 2 - 100,
                    10,
                    200
            );
            addDrawableChild(textureSearch);

            // Add the process button at the bottom of the right panel
            processButton = ButtonWidget.builder(
                            Text.translatable("repal.config.process"),
                            this::onProcessClick
                    )
                    .dimensions(width - SIDE_PANEL_WIDTH, height - 30, SIDE_PANEL_WIDTH, 20)
                    .build();
            addDrawableChild(processButton);

            // Add additional UI elements (layer and preset buttons)
            addDrawableChildren();
        }

        private void onProcessClick(ButtonWidget button) {
            TextureProcessor.processAllTextures();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);

            textureSearch.render(context, mouseX, mouseY, delta);
            layerUI.render(context, mouseX, mouseY, delta);
            texturePreviewUI.render(context, mouseX, mouseY);
            processButton.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (texturePreviewUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (layerUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }


            if (processingUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (processingUI.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (texturePreviewUI.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (layerUI.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            if (processingUI.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (layerUI.charTyped(chr, modifiers)) {
                return true;
            }

            if (processingUI.charTyped(chr, modifiers)) {
                return true;
            }
            return super.charTyped(chr, modifiers);
        }

        @Override
        public void tick() {
            super.tick();
            textureSearch.tick();
            processingUI.tick();
        }

        public void updateSliderValues(LayerInfo layer) {
            if (processingUI != null) {
                processingUI.updateValues(layer);
            }
        }

        private void addDrawableChildren() {
            for (ButtonWidget button : layerUI.getButtons()) {
                addDrawableChild(button);
            }

        }

        public <T extends Element & Drawable & Selectable> void addDrawableC(T w) {
            addDrawableChild(w);

        }
    }
}