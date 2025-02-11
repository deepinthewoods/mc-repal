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
import ninja.trek.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        private int previewScrollOffset = 0;
        private int columnsPerRow;
        private int currentScrollRow = 0;
        private ButtonWidget scrollUpButton;
        private ButtonWidget scrollDownButton;
        private List<Identifier> currentTextures;

        // Layout constants
        private static final int PREVIEW_SIZE = 64;
        private static final int PREVIEW_SPACING = 16;
        private static final int CONFIG_HEIGHT = 200;
        private static final int PREVIEWS_START_Y = CONFIG_HEIGHT + 20;
        private static final int SIDE_PANEL_WIDTH = 200;
        private static final int UI_SPACING = 10;
        // Add tracking variables for settings changes
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

            // Initialize cloth config on the left side
            initializeConfigUI(0, 0, mainWidth);

            // Initialize layer management on the right side
            layerUI = new LayerManagementUI(client, width - SIDE_PANEL_WIDTH, 0, SIDE_PANEL_WIDTH);
            layerUI.init();

            // Initialize preset management below layer management
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

            // Add process button at the bottom of the right panel
            processButton = ButtonWidget.builder(
                            Text.translatable("repal.config.process"),
                            this::onProcessClick
                    )
                    .dimensions(width - SIDE_PANEL_WIDTH, height - 30, SIDE_PANEL_WIDTH, 20)
                    .build();
            addDrawableChild(processButton);

            // Initialize scroll buttons
            initializeScrollButtons(mainWidth);

            // Load textures and initialize the list
            TextureManager.loadTextures(client.getResourceManager());

            // Initialize columnsPerRow before updating texture list
            columnsPerRow = Math.max(1, (mainWidth - 20) / (PREVIEW_SIZE + PREVIEW_SPACING));

            // Update texture list - this will populate currentTextures
            updateTextureList();

            // Add all UI elements to drawable children
            addDrawableChildren();

            Repal.LOGGER.info("Screen initialized with {} textures",
                    currentTextures != null ? currentTextures.size() : 0);
        }

        // In RepalModMenu.java
        private void checkSettingsChanges() {
            LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
            if (activeLayer == null) return;

            boolean contrastChanged = false;
            boolean saturationChanged = false;
            boolean paletteChanged = false;

            // Check individual setting changes
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

        private void initializeConfigUI(int x, int y, int width) {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(this)
                    .setTitle(Text.translatable("repal.config.title"))
                    .setSavingRunnable(() -> {
                        RepalConfig.save();
                        ProcessedTextureCache.clearCache();
                        updateTextureList();
                    })
                    .setTransparentBackground(true);

            ConfigCategory general = builder.getOrCreateCategory(
                    Text.translatable("repal.config.category.general")
            );
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Add entries for the active layer
            LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
            if (activeLayer != null) {
                // Contrast Slider
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

                // Saturation Slider
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

                // Palette Selection
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
            // Add layer UI buttons
            for (ButtonWidget button : layerUI.getButtons()) {
                addDrawableChild(button);
            }

            // Add preset UI buttons
            for (ButtonWidget button : presetUI.getButtons()) {
                addDrawableChild(button);
            }
        }

        private void updateTextureList() {
            // Add debug logging
            Repal.LOGGER.debug("Starting updateTextureList");

            // Get the active layer - with validation
            LayerManager layerManager = LayerManager.getInstance();
            if (layerManager == null) {
                Repal.LOGGER.error("LayerManager instance is null");
                return;
            }

            LayerInfo activeLayer = layerManager.getActiveLayer();
            Repal.LOGGER.debug("Active layer: {}", activeLayer != null ? activeLayer.getName() : "null");

            // Initialize the list if null
            if (currentTextures == null) {
                currentTextures = new ArrayList<>();
            } else {
                currentTextures.clear(); // Clear existing list before updating
            }

            // Update the list based on active layer or get all textures
            if (activeLayer != null) {
                Set<Identifier> layerTextures = activeLayer.getTextures();
                if (layerTextures != null && !layerTextures.isEmpty()) {
                    currentTextures.addAll(layerTextures);
                    Repal.LOGGER.info("Updated texture list from active layer: {} textures", currentTextures.size());
                } else {
                    Repal.LOGGER.warn("Active layer has no textures, falling back to all textures");
                    currentTextures.addAll(TextureManager.getAllBlockTextures());
                }
            } else {
                Repal.LOGGER.info("No active layer, loading all block textures");
                currentTextures.addAll(TextureManager.getAllBlockTextures());
            }

            // Validate the texture list
            currentTextures.removeIf(texture -> {
                if (texture == null) {
                    Repal.LOGGER.warn("Removed null texture from list");
                    return true;
                }
                return false;
            });

            // Reset scroll position when list updates
            currentScrollRow = 0;

            // Update scroll buttons
            if (scrollUpButton != null && scrollDownButton != null) {
                scrollUpButton.active = false;
                int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
                int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
                scrollDownButton.active = totalRows > visibleRows;

                Repal.LOGGER.debug("Scroll state - visibleRows: {}, totalRows: {}, canScrollDown: {}",
                        visibleRows, totalRows, scrollDownButton.active);
            } else {
                Repal.LOGGER.warn("Scroll buttons not initialized");
            }

            // Log final state
            Repal.LOGGER.info("Texture list update complete. Total textures: {}", currentTextures.size());
        }

        private void onProcessClick(ButtonWidget button) {
            TextureProcessor.processAllTextures();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            checkSettingsChanges();

            super.render(context, mouseX, mouseY, delta);

            // Render cloth config screen
            if (clothConfigScreen != null) {
                clothConfigScreen.render(context, mouseX, mouseY, delta);
            }

            // Render texture search
            textureSearch.render(context, mouseX, mouseY, delta);

            // Render layer management UI
            layerUI.render(context, mouseX, mouseY, delta);

            // Render preset management UI
            presetUI.render(context, mouseX, mouseY, delta);

            // Render texture previews
            renderTexturePreviews(context, mouseX, mouseY);

            // Render process button
            processButton.render(context, mouseX, mouseY, delta);
        }

        private void renderTexturePreviews(DrawContext context, int mouseX, int mouseY) {
            if (currentTextures == null || currentTextures.isEmpty()) {
                Repal.LOGGER.debug("No textures to render");
                return;
            }

            LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
            if (activeLayer == null) {
                Repal.LOGGER.warn("No active layer available for preview rendering");
                return;
            }

            int mainWidth = width - SIDE_PANEL_WIDTH - UI_SPACING;
            int availableWidth = mainWidth - 20; // 10px padding on each side

            // Calculate preview sizes with reduced spacing between original and processed
            int spacingBetweenPreviews = 2;
            int totalPreviewWidth = PREVIEW_SIZE * 2 + spacingBetweenPreviews;
            columnsPerRow = Math.max(1, availableWidth / (totalPreviewWidth + PREVIEW_SPACING));

            int x = 10;
            int y = PREVIEWS_START_Y;

            // Calculate visible rows and total rows
            int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
            int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
            int startIndex = currentScrollRow * columnsPerRow;
            int endIndex = Math.min(startIndex + (visibleRows * columnsPerRow), currentTextures.size());

            // Update scroll buttons
            scrollUpButton.active = currentScrollRow > 0;
            scrollDownButton.active = currentScrollRow < totalRows - visibleRows;

            // Render texture previews
            for (int i = startIndex; i < endIndex; i++) {
                if (i >= currentTextures.size()) break;

                Identifier texture = currentTextures.get(i);
                int col = (i - startIndex) % columnsPerRow;
                int row = (i - startIndex) / columnsPerRow;

                // Calculate positions for original and processed textures
                int baseX = x + col * (totalPreviewWidth + PREVIEW_SPACING);
                int previewY = y + row * (PREVIEW_SIZE + PREVIEW_SPACING);

                try {
                    // Draw selection highlight if texture is selected
                    if (TextureManager.isSelected(texture)) {
                        context.fill(
                                baseX - 2,
                                previewY - 2,
                                baseX + totalPreviewWidth + 2,
                                previewY + PREVIEW_SIZE + 2,
                                0xFFFFFF00
                        );
                    }

                    // Draw original texture
                    context.drawTexture(
                            texture,
                            baseX,
                            previewY,
                            0, 0,
                            PREVIEW_SIZE,
                            PREVIEW_SIZE,
                            PREVIEW_SIZE,
                            PREVIEW_SIZE
                    );

                    // Get and draw processed texture preview
                    try {
                        Identifier processedTexture = ProcessedTextureCache.getProcessedTexture(texture, activeLayer);
                        if (processedTexture != null) {
                            context.drawTexture(
                                    processedTexture,
                                    baseX + PREVIEW_SIZE + spacingBetweenPreviews,
                                    previewY,
                                    0, 0,
                                    PREVIEW_SIZE,
                                    PREVIEW_SIZE,
                                    PREVIEW_SIZE,
                                    PREVIEW_SIZE
                            );
                        } else {
                            // If processing failed, draw a red tint over the original texture
                            context.drawTexture(
                                    texture,
                                    baseX + PREVIEW_SIZE + spacingBetweenPreviews,
                                    previewY,
                                    0, 0,
                                    PREVIEW_SIZE,
                                    PREVIEW_SIZE,
                                    PREVIEW_SIZE,
                                    PREVIEW_SIZE
                            );
                            context.fill(
                                    baseX + PREVIEW_SIZE + spacingBetweenPreviews,
                                    previewY,
                                    baseX + PREVIEW_SIZE * 2 + spacingBetweenPreviews,
                                    previewY + PREVIEW_SIZE,
                                    0x40FF0000 // Semi-transparent red
                            );
                        }
                    } catch (Exception e) {
                        Repal.LOGGER.error("Failed to render processed texture for " + texture, e);
                        // Draw error indicator
                        context.fill(
                                baseX + PREVIEW_SIZE + spacingBetweenPreviews,
                                previewY,
                                baseX + PREVIEW_SIZE * 2 + spacingBetweenPreviews,
                                previewY + PREVIEW_SIZE,
                                0x80FF0000 // More opaque red for errors
                        );
                    }

                    // Draw texture name label centered under both previews
                    String textureName = texture.getPath().substring(
                            texture.getPath().lastIndexOf('/') + 1,
                            texture.getPath().lastIndexOf('.')
                    );

                    int textX = baseX + (totalPreviewWidth / 2) - (client.textRenderer.getWidth(textureName) / 2);
                    context.drawTextWithShadow(
                            client.textRenderer,
                            textureName,
                            textX,
                            previewY + PREVIEW_SIZE + 2,
                            0xFFFFFF
                    );

                    // Draw hover tooltip
                    if (mouseX >= baseX && mouseX < baseX + totalPreviewWidth &&
                            mouseY >= previewY && mouseY < previewY + PREVIEW_SIZE) {

                        List<Text> tooltip = new ArrayList<>();
                        tooltip.add(Text.literal(textureName));

                        // Add processing info if mouse is over processed preview
                        if (mouseX >= baseX + PREVIEW_SIZE + spacingBetweenPreviews) {
                            tooltip.add(Text.literal("Contrast: " + activeLayer.getContrast())
                                    .formatted(Formatting.GRAY));
                            tooltip.add(Text.literal("Saturation: " + activeLayer.getSaturation())
                                    .formatted(Formatting.GRAY));
                            tooltip.add(Text.literal("Palette: " + activeLayer.getPalette())
                                    .formatted(Formatting.GRAY));
                        }

                        context.drawTooltip(
                                client.textRenderer,
                                tooltip,
                                mouseX,
                                mouseY
                        );
                    }

                } catch (Exception e) {
                    Repal.LOGGER.error("Failed to render preview for texture: " + texture, e);
                    // Draw error indicator
                    context.fill(
                            baseX,
                            previewY,
                            baseX + totalPreviewWidth,
                            previewY + PREVIEW_SIZE,
                            0x80FF0000
                    );
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Handle texture preview clicks first, if in the preview area
            int mainWidth = width - SIDE_PANEL_WIDTH - UI_SPACING;

            // Only process clicks in the preview area
            if (mouseY >= PREVIEWS_START_Y && mouseY < height - 30 && mouseX >= 10 && mouseX < mainWidth) {
                int spacingBetweenPreviews = 2;
                int totalPreviewWidth = PREVIEW_SIZE * 2 + spacingBetweenPreviews;
                int totalItemWidth = totalPreviewWidth + PREVIEW_SPACING;

                // Calculate which preview was clicked
                int col = (int)((mouseX - 10) / totalItemWidth);
                int row = (int)((mouseY - PREVIEWS_START_Y) / (PREVIEW_SIZE + PREVIEW_SPACING));

                // Calculate index in the texture list
                int index = (currentScrollRow + row) * columnsPerRow + col;

                // Verify click is within valid bounds
                if (col >= 0 && col < columnsPerRow && index >= 0 && index < currentTextures.size()) {
                    Identifier clickedTexture = currentTextures.get(index);
                    TextureManager.toggleTextureSelection(clickedTexture);
                    return true;
                }
            }

            // Then handle UI clicks
            if (layerUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (presetUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            // Handle ClothConfig clicks last
            if (clothConfigScreen != null && clothConfigScreen.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            // Let super handle any remaining clicks
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            // Only handle scrolling if mouse is over the preview area
            if (mouseY >= PREVIEWS_START_Y) {
                int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
                int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);

                // Scroll up
                if (verticalAmount > 0 && currentScrollRow > 0) {
                    currentScrollRow--;
                    return true;
                }
                // Scroll down
                else if (verticalAmount < 0 && currentScrollRow < totalRows - visibleRows) {
                    currentScrollRow++;
                    return true;
                }
            }

            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        // Update scroll button handlers
        private void initializeScrollButtons(int mainWidth) {
            int buttonWidth = 60;
            scrollUpButton = ButtonWidget.builder(
                            Text.literal("▲"),
                            button -> {
                                if (currentScrollRow > 0) {
                                    currentScrollRow--;
                                }
                            }
                    ).dimensions(mainWidth / 2 - buttonWidth - 5, PREVIEWS_START_Y - 20, buttonWidth, 20)
                    .build();

            scrollDownButton = ButtonWidget.builder(
                            Text.literal("▼"),
                            button -> {
                                int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
                                int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
                                if (currentScrollRow < totalRows - visibleRows) {
                                    currentScrollRow++;
                                }
                            }
                    ).dimensions(mainWidth / 2 + 5, PREVIEWS_START_Y - 20, buttonWidth, 20)
                    .build();

            addDrawableChild(scrollUpButton);
            addDrawableChild(scrollDownButton);
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
    }
}