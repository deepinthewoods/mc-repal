package ninja.trek.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;
import ninja.trek.Repal;
import ninja.trek.RepalClient;
import ninja.trek.ImageProcessor;
import ninja.trek.RepalResourceReloadListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class RepalConfigScreen extends Screen {
    private static final Identifier GRASS_PREVIEW = Identifier.of("minecraft", "textures/block/grass_block_top.png");
    private final Screen parent;
    private SliderWidget contrastSlider;
    private SliderWidget saturationSlider;
    private ButtonWidget paletteButton;
    private TextFieldWidget packNameField;
    private ButtonWidget processButton;
    private BufferedImage originalPreview;

    public RepalConfigScreen(Screen parent) {
        super(Text.translatable("repal.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = height / 4;

        // Load preview texture
        loadPreviewTexture();
    }

    private void loadPreviewTexture() {
        try {
            // Load original grass texture
            InputStream stream = client.getResourceManager()
                    .getResource(GRASS_PREVIEW)
                    .get()
                    .getInputStream();
            originalPreview = ImageIO.read(stream);
            updatePreview();
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to load preview texture", e);
        }
    }

    private void updatePreview() {
        if (originalPreview == null) return;
        try {
            // Process the preview image
            BufferedImage processed = ImageProcessor.processImage(
                    originalPreview,
                    RepalResourceReloadListener.getCurrentPaletteColors(),
                    Repal.getPreContrast(),
                    Repal.getPreSaturation()
            );

            // Convert BufferedImage to NativeImage
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(processed, "png", os);
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            NativeImage nativeImage = NativeImage.read(is);

            // Update the texture
            RepalClient.updatePreviewTexture(nativeImage);
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to update preview", e);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        // Draw the original texture
        context.drawTexture(
                GRASS_PREVIEW,         // texture identifier
                width / 4 - 32,        // x
                height / 2,            // y
                0,                     // z (depth)
                0.0f,                  // u coordinate start
                0.0f,                  // v coordinate start
                64,                    // width of the drawn quad
                64,                    // height of the drawn quad
                64,                    // texture width
                64                     // texture height
        );

        // Draw the processed texture
        context.drawTexture(
                RepalClient.PREVIEW_TEXTURE_ID, // texture identifier
                3 * width / 4 - 32,               // x
                height / 2,                       // y
                0,                                // z (depth)
                0.0f,                             // u coordinate start
                0.0f,                             // v coordinate start
                64,                               // width of the drawn quad
                64,                               // height of the drawn quad
                64,                               // texture width
                64                                // texture height
        );


        // Labels
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("repal.preview.original"),
                width / 4,
                height / 2 - 20,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("repal.preview.processed"),
                3 * width / 4,
                height / 2 - 20,
                0xFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        super.removed();
    }
}