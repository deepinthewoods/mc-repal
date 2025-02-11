package ninja.trek.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
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

    public static class MergedConfigScreen extends Screen {
        private final Screen parent;
        private Screen clothConfigScreen;
        private static final Identifier GRASS_PREVIEW = Identifier.of("minecraft", "textures/block/grass_block_side.png");
        private BufferedImage originalPreview;

        // Entry references
        private IntegerSliderEntry contrastEntry;
        private IntegerSliderEntry saturationEntry;
        private DropdownBoxEntry<String> paletteEntry;

        // Last known values
        private int lastContrast;
        private int lastSaturation;
        private String lastPalette;

        public MergedConfigScreen(Screen parent) {
            super(Text.translatable("repal.config.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // Build the Cloth Config UI
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("repal.config.title"))
                    .setSavingRunnable(RepalConfig::save);

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
            var saturationEntry = entryBuilder.startIntSlider(
                            Text.translatable("repal.config.saturation"),
                            RepalConfig.get().preSaturation(),
                            Repal.MIN_ADJUSTMENT,
                            Repal.MAX_ADJUSTMENT)
                    .setDefaultValue(0)
                    .setTooltip(Text.translatable("repal.tooltip.saturation"))
                    .setSaveConsumer(value -> RepalConfig.get().setPreSaturation(value))
                    .build();
            general.addEntry(saturationEntry);

            // Find palettes
            List<PaletteInfo> palettes = RepalResourceReloadListener.getAvailablePalettes();
            Repal.LOGGER.info("Available palettes for menu: {}", palettes.stream()
                    .map(PaletteInfo::getName)
                    .collect(Collectors.joining(", ")));

// Create the dropdown
            var paletteEntry = entryBuilder.startStringDropdownMenu(
                            Text.translatable("repal.config.palette"),
                            RepalConfig.get().selectedPalette())
                    .setDefaultValue(RepalResourceReloadListener.getAvailablePalettes()
                            .stream()
                            .findFirst()
                            .map(PaletteInfo::getName)
                            .orElse("pal1"))  // Use first available palette as default
                    .setSelections(palettes.stream()
                            .map(PaletteInfo::getName)
                            .collect(Collectors.toSet()))
                    .setTooltip(Text.translatable("repal.tooltip.palette"))
                    .setSaveConsumer(value -> {
                        Repal.LOGGER.info("Selected palette in dropdown: {}", value);
                        RepalConfig.get().setSelectedPalette(value);
                    })
                    .build();

            general.addEntry(paletteEntry);

            // Pack Name Field
            general.addEntry(entryBuilder.startStrField(
                            Text.translatable("repal.config.pack_name"),
                            RepalConfig.get().packName())
                    .setDefaultValue("repal")
                    .setTooltip(Text.translatable("repal.tooltip.pack_name"))
                    .setSaveConsumer(value -> RepalConfig.get().setPackName(value))
                    .build()
            );

            // Build the screen
            clothConfigScreen = builder.build();
            clothConfigScreen.init(client, width, height);

            // Store entry references after initialization
            this.contrastEntry = (IntegerSliderEntry) contrastEntry;
            this.saturationEntry = (IntegerSliderEntry) saturationEntry;
            this.paletteEntry =  paletteEntry;

            // Initialize the preview texture
            loadPreviewTexture();

            // Initialize last known values
            lastContrast = contrastEntry.getValue();
            lastSaturation = saturationEntry.getValue();
            lastPalette = paletteEntry.getValue();
        }

        private void loadPreviewTexture() {
            try {
                var resource = client.getResourceManager()
                        .getResource(GRASS_PREVIEW)
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
            clothConfigScreen.tick();

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

                Repal.LOGGER.info("Updated preview - Contrast: {}, Saturation: {}, Palette: {}",
                        currentContrast, currentSaturation, currentPalette);
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // First, let the Cloth Config screen render its UI
            clothConfigScreen.render(context, mouseX, mouseY, delta);

            // Draw the preview area at the bottom
            int previewAreaHeight = 100;
            int yStart = height - previewAreaHeight + 10;

            // Draw the original texture preview on the left
            context.drawTexture(
                    GRASS_PREVIEW,
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
        }

        @Override
        public void removed() {
            clothConfigScreen.removed();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return clothConfigScreen.keyPressed(keyCode, scanCode, modifiers)
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return clothConfigScreen.mouseClicked(mouseX, mouseY, button)
                    || super.mouseClicked(mouseX, mouseY, button);
        }
    }
}