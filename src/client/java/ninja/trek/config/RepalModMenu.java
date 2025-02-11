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
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ninja.trek.Repal;
import ninja.trek.RepalClient;
import ninja.trek.ImageProcessor;
import ninja.trek.RepalResourceReloadListener;
import ninja.trek.TextureManager;
import ninja.trek.PaletteInfo;

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

        // Entry references
        private IntegerSliderEntry contrastEntry;
        private IntegerSliderEntry saturationEntry;
        private DropdownBoxEntry<String> paletteEntry;

        // Last known values
        private int lastContrast;
        private int lastSaturation;
        private String lastPalette;
        private BufferedImage originalPreview;

        public MergedConfigScreen(Screen parent) {
            super(Text.translatable("repal.config.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean searchHandled = textureSearch.mouseClicked(mouseX, mouseY, button);
            if (searchHandled) {
                setFocused(textureSearch);
                return true;
            }

            boolean configHandled = clothConfigScreen != null && clothConfigScreen.mouseClicked(mouseX, mouseY, button);
            if (configHandled) {
                setFocused(null); // Let Cloth Config handle its own focus
                return true;
            }

            return false;
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

                // Clear the ImageProcessor cache
                ImageProcessor.clearCache();

                // Force texture reload
                if (client != null && client.getTextureManager() != null) {
                    client.getTextureManager().destroyTexture(RepalClient.PREVIEW_TEXTURE_ID);
                }

                // Reload preview texture
                loadPreviewTexture();

                // Store new values
                lastContrast = currentContrast;
                lastSaturation = currentSaturation;
                lastPalette = currentPalette;
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (clothConfigScreen != null) {
                clothConfigScreen.render(context, mouseX, mouseY, delta);
            }

            // Render our texture search box
            textureSearch.render(context, mouseX, mouseY, delta);

            // Draw the preview area
            int previewAreaHeight = 100;
            int yStart = height - previewAreaHeight + 10;

            // Draw the original texture preview on the left
            context.drawTexture(
                    TextureManager.getCurrentPreviewTexture(),
                    width / 4 - 32,
                    yStart,
                    0,
                    0.0f,
                    0.0f,
                    64,
                    64,
                    64,
                    64
            );

            // Draw the processed texture preview on the right
            context.drawTexture(
                    RepalClient.PREVIEW_TEXTURE_ID,
                    3 * width / 4 - 32,
                    yStart,
                    0,
                    0.0f,
                    0.0f,
                    64,
                    64,
                    64,
                    64
            );

            // Draw labels above the previews
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("repal.preview.original"),
                    width / 4,
                    yStart - 12,
                    0xFFFFFF
            );
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("repal.preview.processed"),
                    3 * width / 4,
                    yStart - 12,
                    0xFFFFFF
            );

            // Draw texture search label
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("repal.search.label"),
                    width / 2,
                    25,
                    0xFFFFFF
            );
        }


        @Override
        public void removed() {
            if (clothConfigScreen != null) {
                clothConfigScreen.removed();
            }
        }



       
    }
}