package ninja.trek.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import ninja.trek.LayerInfo;
import ninja.trek.LayerManager;
import ninja.trek.PaletteInfo;
import ninja.trek.ProcessedTextureCache;
import ninja.trek.Repal;
import ninja.trek.RepalResourceReloadListener;
import ninja.trek.TextureManager;
import ninja.trek.TextureProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RepalModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new MergedConfigScreen(parent);
    }

    public class MergedConfigScreen extends Screen {
        private final Screen parent;
        private Screen clothConfigScreen;
        private TextureComboBox textureSearch;
        private LayerManagementUI layerUI;
        private PresetManagementUI presetUI;
        private ButtonWidget processButton;
        private TexturePreviewUI texturePreviewUI;

        // Layout constants
        private static final int CONFIG_HEIGHT = 200;
        private static final int SIDE_PANEL_WIDTH = 200;
        private static final int UI_SPACING = 10;

        // Tracking variables for settings changes
        private int lastContrast;
        private int lastSaturation;
        private String lastPalette;
        private IntegerSliderEntry contrastEntry;
        private IntegerSliderEntry saturationEntry;
        private @NotNull DropdownBoxEntry<String> paletteEntry;

        public MergedConfigScreen(Screen parent) {
            super(Text.translatable("repal.config.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
            if (activeLayer != null) {
                lastContrast = activeLayer.getContrast();
                lastSaturation = activeLayer.getSaturation();
                lastPalette = activeLayer.getPalette();
            }

            int mainWidth = width - SIDE_PANEL_WIDTH - UI_SPACING;

            // Initialize the Cloth Config UI on the left side
            initializeConfigUI(0, 0, mainWidth);

            // Initialize texture preview UI first
            int previewsStartY = CONFIG_HEIGHT + 20;
            int previewAreaX = 10;
            int previewAreaWidth = mainWidth - 20;
            int previewAreaHeight = height - previewsStartY - 20;
            texturePreviewUI = new TexturePreviewUI(this, client, previewAreaX, previewsStartY, previewAreaWidth, previewAreaHeight);
            texturePreviewUI.init();

            // Initialize layer management UI on the right side
            layerUI = new LayerManagementUI(client, width - SIDE_PANEL_WIDTH, 0, SIDE_PANEL_WIDTH);
            layerUI.init();
            // Connect layer UI with preview UI
            layerUI.setTexturePreviewUI(texturePreviewUI);
            layerUI.setConfigScreen(this);

            // Initialize preset management UI below the layer management
            presetUI = new PresetManagementUI(client, width - SIDE_PANEL_WIDTH, 180, SIDE_PANEL_WIDTH);
            presetUI.init();

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

            Repal.LOGGER.info("Screen initialized with texture preview area at ({}, {}) size {}x{}",
                    previewAreaX, previewsStartY, previewAreaWidth, previewAreaHeight);
        }

        private void checkSettingsChanges() {
            LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
            if (activeLayer == null) return;

            boolean contrastChanged = false;
            boolean saturationChanged = false;
            boolean paletteChanged = false;

            if (contrastEntry != null && contrastEntry.getValue() != activeLayer.getContrast()) {
                contrastChanged = true;
                activeLayer.setContrast(contrastEntry.getValue());
            }

            if (saturationEntry != null && saturationEntry.getValue() != activeLayer.getSaturation()) {
                saturationChanged = true;
                activeLayer.setSaturation(saturationEntry.getValue());
            }

            if (paletteEntry != null && !Objects.equals(paletteEntry.getValue(), activeLayer.getPalette())) {
                paletteChanged = true;
                activeLayer.setPalette(paletteEntry.getValue());
            }
        }

        /**
         * Updates the slider and dropdown values when switching between layers.
         * This method is called from LayerManagementUI when the active layer changes.
         */
        // In RepalModMenu.java, replace the updateSliderValues method with:

        // In RepalModMenu.java, replace the updateSliderValues method with:

        // In RepalModMenu.java, replace the updateSliderValues method with:

        public void updateSliderValues(LayerInfo layer) {
            if (layer == null) return;

            // Update slider entries
            if (contrastEntry != null) {
                contrastEntry.setValue(layer.getContrast());
            }
            if (saturationEntry != null) {
                saturationEntry.setValue(layer.getSaturation());
            }

            // Calculate the same width as used in the initial initialization
            int mainWidth = width - SIDE_PANEL_WIDTH - UI_SPACING;

            // Reinitialize the config UI with the correct width
            initializeConfigUI(0, 0, mainWidth);

            // Update last known values to match the new layer
            lastContrast = layer.getContrast();
            lastSaturation = layer.getSaturation();
            lastPalette = layer.getPalette();

            // Now reinitialize the cloth config screen with the correct width
            if (clothConfigScreen != null) {
                clothConfigScreen.init(client, mainWidth, height);
            }
        }

        private void initializeConfigUI(int x, int y, int width) {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(this)
                    .setTitle(Text.translatable("repal.config.title"))
                    .setSavingRunnable(() -> {
                        RepalConfig.save();
                        ProcessedTextureCache.clearCache();
                        texturePreviewUI.updateTextureList();
                    })
                    .setTransparentBackground(true);

            ConfigCategory general = builder.getOrCreateCategory(
                    Text.translatable("repal.config.category.general")
            );
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
            if (activeLayer != null) {
                contrastEntry = entryBuilder.startIntSlider(
                                Text.translatable("repal.config.contrast"),
                                activeLayer.getContrast(),
                                Repal.MIN_ADJUSTMENT,
                                Repal.MAX_ADJUSTMENT
                        )
                        .setDefaultValue(0)
                        .setTooltip(Text.translatable("repal.tooltip.contrast"))
                        .build();
                general.addEntry(contrastEntry);

                saturationEntry = entryBuilder.startIntSlider(
                                Text.translatable("repal.config.saturation"),
                                activeLayer.getSaturation(),
                                Repal.MIN_ADJUSTMENT,
                                Repal.MAX_ADJUSTMENT
                        )
                        .setDefaultValue(0)
                        .setTooltip(Text.translatable("repal.tooltip.saturation"))
                        .build();
                general.addEntry(saturationEntry);

                List<String> availablePaletteNames = RepalResourceReloadListener.getAvailablePalettes()
                        .stream()
                        .map(PaletteInfo::getName)
                        .collect(Collectors.toList());

                if (!availablePaletteNames.isEmpty()) {
                    paletteEntry = entryBuilder.<String>startDropdownMenu(
                                    Text.translatable("repal.config.palette"),
                                    activeLayer.getPalette(),
                                    s -> s,
                                    s -> Text.literal(s)
                            )
                            .setSelections(availablePaletteNames)
                            .setDefaultValue(availablePaletteNames.get(0))
                            .setTooltip(Text.translatable("repal.tooltip.palette"))
                            .build();
                    general.addEntry(paletteEntry);
                }
            }

            clothConfigScreen = builder.build();
            clothConfigScreen.init(client, width, height);
        }

        private void addDrawableChildren() {
            for (ButtonWidget button : layerUI.getButtons()) {
                addDrawableChild(button);
            }
            for (ButtonWidget button : presetUI.getButtons()) {
                addDrawableChild(button);
            }
        }

        private void onProcessClick(ButtonWidget button) {
            TextureProcessor.processAllTextures();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            checkSettingsChanges();
            super.render(context, mouseX, mouseY, delta);

            if (clothConfigScreen != null) {
                clothConfigScreen.render(context, mouseX, mouseY, delta);
            }

            textureSearch.render(context, mouseX, mouseY, delta);
            layerUI.render(context, mouseX, mouseY, delta);
            presetUI.render(context, mouseX, mouseY, delta);

            // Delegate preview rendering to the TexturePreviewUI instance
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
            if (presetUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (clothConfigScreen != null && clothConfigScreen.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
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
            if (presetUI.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (clothConfigScreen != null && clothConfigScreen.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (layerUI.charTyped(chr, modifiers)) {
                return true;
            }
            if (presetUI.charTyped(chr, modifiers)) {
                return true;
            }
            if (clothConfigScreen != null && clothConfigScreen.charTyped(chr, modifiers)) {
                return true;
            }
            return super.charTyped(chr, modifiers);
        }

        @Override
        public void tick() {
            super.tick();
            if (clothConfigScreen != null) {
                clothConfigScreen.tick();
            }
            textureSearch.tick();
        }

        public void addDrawableC(ButtonWidget w) {
            addDrawableChild( w);
        }
    }
}
