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
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ninja.trek.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
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
        private IntegerSliderEntry contrastEntry;
        private IntegerSliderEntry saturationEntry;
        private DropdownBoxEntry<String> paletteEntry;
        private int lastContrast;
        private int lastSaturation;
        private String lastPalette;
        private BufferedImage originalPreview;

        // New fields for texture preview list
        private static final int PREVIEW_SIZE = 64;
        private static final int PREVIEW_SPACING = 16;
        private static final int CONFIG_HEIGHT = 200; // Height needed for config UI
        private static final int PREVIEWS_START_Y = CONFIG_HEIGHT + 20; // Starting Y position after the search box
        private int previewScrollOffset = 0;
        // Dynamic grid layout
        private int columnsPerRow;
        private int currentScrollRow = 0;
        private ButtonWidget scrollUpButton;
        private ButtonWidget scrollDownButton;
        private List<Identifier> currentTextures;

        public MergedConfigScreen(Screen parent) {
            super(Text.translatable("repal.config.title"));
            this.parent = parent;
        }


        protected void initializeConfigUI() {
            // Build the Cloth Config UI first
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(this)
                    .setTitle(Text.translatable("repal.config.title"))
                    .setSavingRunnable(RepalConfig::save)
                    .setTransparentBackground(true);

            ConfigCategory general = builder.getOrCreateCategory(
                    Text.translatable("repal.config.category.general")
            );

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Pre-Contrast Slider
            var contrastEntry = entryBuilder.startIntSlider(
                            Text.translatable("repal.config.contrast"),
                            RepalConfig.get().preContrast(),
                            Repal.MIN_ADJUSTMENT,
                            Repal.MAX_ADJUSTMENT)
                    .setDefaultValue(0)
                    .setTooltip(Text.translatable("repal.tooltip.contrast"))
                    .setSaveConsumer(value -> RepalConfig.get().setPreContrast(value))
                    .build();
            general.addEntry(contrastEntry);

            // Pre-Saturation Slider
            var saturationSliderEntry = entryBuilder.startIntSlider(
                            Text.translatable("repal.config.saturation"),
                            RepalConfig.get().preSaturation(),
                            Repal.MIN_ADJUSTMENT,
                            Repal.MAX_ADJUSTMENT)
                    .setDefaultValue(0)
                    .setTooltip(Text.translatable("repal.tooltip.saturation"))
                    .setSaveConsumer(value -> RepalConfig.get().setPreSaturation(value))
                    .build();
            general.addEntry(saturationSliderEntry);

            // Get available palettes
            List<String> availablePaletteNames = RepalResourceReloadListener.getAvailablePalettes()
                    .stream()
                    .map(PaletteInfo::getName)
                    .collect(Collectors.toList());

            // If no palettes available, add a default one
            if (availablePaletteNames.isEmpty()) {
                availablePaletteNames.add("pal1");
            }

            // Get current selected palette, fallback to first available if current is invalid
            String currentPalette = RepalConfig.get().selectedPalette();
            if (!availablePaletteNames.contains(currentPalette)) {
                currentPalette = availablePaletteNames.get(0);
            }

            var paletteDropdownEntry = entryBuilder.<String>startDropdownMenu(
                            Text.translatable("repal.config.palette"),
                            currentPalette,
                            s -> s,                      // Converts the string input to a String
                            s -> Text.literal(s)         // Converts the String to a Text for display
                    )
                    .setSelections(availablePaletteNames)
                    .setDefaultValue(availablePaletteNames.get(0))
                    .setTooltip(Text.translatable("repal.tooltip.palette"))
                    .setSaveConsumer(selected -> RepalConfig.get().setSelectedPalette(selected))
                    .build();
            general.addEntry(paletteDropdownEntry);

            // Build and initialize the Cloth Config screen
            clothConfigScreen = builder.build();
            clothConfigScreen.init(client, width, height);

            // Create and position the texture search box
            textureSearch = new TextureComboBox(client, width / 2 - 100, 10, 200);
            if (this.children().contains(textureSearch)) {
                this.remove(textureSearch);
            }
            addDrawableChild(textureSearch);

            // Store entry references
            this.contrastEntry = (IntegerSliderEntry) contrastEntry;
            this.saturationEntry = (IntegerSliderEntry) saturationSliderEntry;
            this.paletteEntry = paletteDropdownEntry;

            // Initialize last known values
            lastContrast = contrastEntry.getValue();
            lastSaturation = saturationSliderEntry.getValue();
            lastPalette = paletteDropdownEntry.getValue();

            // Load textures into the manager
            TextureManager.loadTextures(client.getResourceManager());

            // Calculate how many previews can fit
            int availableHeight = height - PREVIEWS_START_Y - 20; // Leave space at bottom
            int maxPreviewsVisible = availableHeight / (PREVIEW_SIZE + PREVIEW_SPACING);




        }

        @Override
        protected void init() {
            // Initialize the config UI components (keeping existing config initialization code)
            initializeConfigUI();

            // Calculate dynamic grid layout
            calculateGridLayout();

            // Initialize texture list
            currentTextures = TextureManager.getBlockTextures();

            // Add scroll buttons with updated positioning
            int buttonWidth = 60;
            scrollUpButton = ButtonWidget.builder(Text.literal("▲ Up"), button -> {
                if (currentScrollRow > 0) currentScrollRow--;
            }).dimensions(width / 2 - buttonWidth - 5, PREVIEWS_START_Y - 20, buttonWidth, 20).build();

            scrollDownButton = ButtonWidget.builder(Text.literal("Down ▼"), button -> {
                int maxRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
                int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
                if (currentScrollRow < maxRows - visibleRows) currentScrollRow++;
            }).dimensions(width / 2 + 5, PREVIEWS_START_Y - 20, buttonWidth, 20).build();

            addDrawableChild(scrollUpButton);
            addDrawableChild(scrollDownButton);
        }

        private void calculateGridLayout() {
            // Calculate preview pair width (original + processed + spacing between them)
            int previewPairWidth = (PREVIEW_SIZE * 2) + 8; // Two previews plus 8px spacing between them

            // Calculate available width accounting for side padding
            int availableWidth = width - 40; // 20px padding on each side

            // Calculate how many preview pairs can fit per row
            // Include PREVIEW_SPACING between each pair
            columnsPerRow = Math.max(1, (availableWidth + PREVIEW_SPACING) / (previewPairWidth + PREVIEW_SPACING));

            // Calculate available height for previews
            int availableHeight = height - PREVIEWS_START_Y - 20; // 20px bottom padding

            // Calculate maximum visible rows (used for scroll bounds)
            int maxVisibleRows = Math.max(1, availableHeight / (PREVIEW_SIZE + PREVIEW_SPACING));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Render config UI
            if (clothConfigScreen != null) {
                clothConfigScreen.render(context, mouseX, mouseY, delta);
            }

            // Render texture search
            textureSearch.render(context, mouseX, mouseY, delta);

            // Calculate layout dimensions
            int previewPairWidth = PREVIEW_SIZE * 2 + 8; // Width for original + processed + spacing
            int totalWidth = columnsPerRow * (previewPairWidth + PREVIEW_SPACING);
            int startX = (width - totalWidth) / 2;
            int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);

// Render texture grid
            for (int row = 0; row < visibleRows; row++) {
                int currentRow = row + currentScrollRow;
                for (int col = 0; col < columnsPerRow; col++) {
                    int index = (currentRow * columnsPerRow) + col;
                    if (index >= currentTextures.size()) break;

                    Identifier texture = currentTextures.get(index);
                    int x = startX + (col * (previewPairWidth + PREVIEW_SPACING));
                    int y = PREVIEWS_START_Y + (row * (PREVIEW_SIZE + PREVIEW_SPACING));

                    // Draw original texture
                    context.drawTexture(
                            texture,
                            x,
                            y,
                            0,
                            0.0f,
                            0.0f,
                            PREVIEW_SIZE,
                            PREVIEW_SIZE,
                            PREVIEW_SIZE,
                            PREVIEW_SIZE
                    );

                    // Draw processed version
                    try {
                        Identifier processedTextureId = ProcessedTextureCache.getProcessedTexture(texture);
                        context.drawTexture(
                                processedTextureId,
                                x + PREVIEW_SIZE + 2, // Original width + spacing
                                y,
                                0,
                                0.0f,
                                0.0f,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE
                        );
                    } catch (Exception e) {
                        Repal.LOGGER.error("Failed to draw processed texture", e);
                        context.fill(
                                x + PREVIEW_SIZE + 16,
                                y,
                                x + PREVIEW_SIZE * 2 + 16,
                                y + PREVIEW_SIZE,
                                0x80FF0000
                        );
                    }

                    // Draw selection highlight if this is the current texture
                    if (texture.equals(TextureManager.getCurrentPreviewTexture())) {
                        context.drawBorder(
                                x - 2,
                                y - 2,
                                previewPairWidth + 4,
                                PREVIEW_SIZE + 4,
                                0xFFFFFF00
                        );
                    }

                    // Draw texture name centered below the preview pair
                    String name = texture.getPath().substring(
                            texture.getPath().lastIndexOf('/') + 1,
                            texture.getPath().lastIndexOf('.')
                    );
                    context.drawCenteredTextWithShadow(
                            textRenderer,
                            Text.literal(name),
                            x + (previewPairWidth / 2),
                            y + PREVIEW_SIZE + 2,
                            0xFFFFFF
                    );
                }
            }

            // Update scroll button states
            int maxRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
            scrollUpButton.active = currentScrollRow > 0;
            scrollDownButton.active = currentScrollRow < maxRows - visibleRows;

            // Render scroll buttons
            scrollUpButton.render(context, mouseX, mouseY, delta);
            scrollDownButton.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // First check if click is in the preview area
            if (mouseY >= PREVIEWS_START_Y) {
                // Calculate layout dimensions for preview grid
                int previewPairWidth = PREVIEW_SIZE * 2 + 8; // Width for original + processed + spacing
                int totalWidth = columnsPerRow * (previewPairWidth + PREVIEW_SPACING);
                int startX = (width - totalWidth) / 2;

                // Calculate which row and column was clicked
                int relativeX = (int)(mouseX - startX);
                int relativeY = (int)(mouseY - PREVIEWS_START_Y);

                // Calculate grid position
                int col = relativeX / (previewPairWidth + PREVIEW_SPACING);
                int row = relativeY / (PREVIEW_SIZE + PREVIEW_SPACING);

                // Verify click is within valid bounds
                if (col >= 0 && col < columnsPerRow) {
                    int index = ((row + currentScrollRow) * columnsPerRow) + col;
                    if (index >= 0 && index < currentTextures.size()) {
                        TextureManager.setCurrentPreviewTexture(currentTextures.get(index));
                        return true;
                    }
                }
            }

            // Handle other UI element clicks
            if (clothConfigScreen != null && clothConfigScreen.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            // Handle texture search clicks
            if (textureSearch.isMouseOver(mouseX, mouseY)) {
                return textureSearch.mouseClicked(mouseX, mouseY, button);
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (verticalAmount != 0 && mouseY >= PREVIEWS_START_Y) {
                int maxRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
                int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);

                if (verticalAmount > 0 && currentScrollRow > 0) {
                    currentScrollRow--;
                    return true;
                } else if (verticalAmount < 0 && currentScrollRow < maxRows - visibleRows) {
                    currentScrollRow++;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (getFocused() == textureSearch) {
                if (textureSearch.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return clothConfigScreen != null && clothConfigScreen.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (getFocused() == textureSearch && textureSearch.charTyped(chr, modifiers)) {
                return true;
            }
            return clothConfigScreen != null && clothConfigScreen.charTyped(chr, modifiers);
        }

        private void loadPreviewTexture() {
            try {
                var resource = client.getResourceManager()
                        .getResource(TextureManager.getCurrentPreviewTexture())
                        .orElseThrow();
                try (InputStream stream = resource.getInputStream()) {
                    BufferedImage originalImage = ImageIO.read(stream);
                    if (originalImage != null) {
                        originalPreview = originalImage;
                        updatePreview();
                    } else {
                        Repal.LOGGER.error("Failed to load preview texture - image is null");
                    }
                }
            } catch (Exception e) {
                Repal.LOGGER.error("Failed to load preview texture", e);
            }
        }

        private NativeImage bufferedImageToNativeImage(BufferedImage image) {
            NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    nativeImage.setColor(x, y, abgr);
                }
            }
            return nativeImage;
        }

        private void updatePreview() {
            if (originalPreview == null) return;
            try {
                BufferedImage processed = ImageProcessor.processImage(
                        originalPreview,
                        RepalResourceReloadListener.getCurrentPaletteColors(),
                        contrastEntry.getValue(),
                        saturationEntry.getValue()
                );
                NativeImage nativeImage = bufferedImageToNativeImage(processed);
                MinecraftClient.getInstance().execute(() -> {
                    RepalClient.updatePreviewTexture(nativeImage);
                });
            } catch (Exception e) {
                Repal.LOGGER.error("Failed to update preview", e);
            }
        }

        @Override
        public void tick() {
            if (clothConfigScreen != null) {
                clothConfigScreen.tick();
            }

            // Poll current values
            int currentContrast = contrastEntry.getValue();
            int currentSaturation = saturationEntry.getValue();
            String currentPalette = paletteEntry.getValue();

            // Check if any values have changed
            if (currentContrast != lastContrast ||
                    currentSaturation != lastSaturation ||
                    !currentPalette.equals(lastPalette)) {
                // Update RepalConfig with new values
                RepalConfig.get().setPreContrast(currentContrast);
                RepalConfig.get().setPreSaturation(currentSaturation);
                RepalConfig.get().setSelectedPalette(currentPalette);

                // Clear all caches
                ImageProcessor.clearCache();
                ProcessedTextureCache.clearCache();

                // Store new values
                lastContrast = currentContrast;
                lastSaturation = currentSaturation;
                lastPalette = currentPalette;
            }
        }


        @Override
        public void removed() {
            if (clothConfigScreen != null) {
                clothConfigScreen.removed();
            }
        }




    }
}