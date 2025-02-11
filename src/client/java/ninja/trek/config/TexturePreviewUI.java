package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import ninja.trek.LayerInfo;
import ninja.trek.LayerManager;
import ninja.trek.ProcessedTextureCache;
import ninja.trek.Repal;
import ninja.trek.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TexturePreviewUI {
    private final RepalModMenu.MergedConfigScreen parent;
    private final MinecraftClient client;
    private final int areaX;
    private final int areaY;
    private final int areaWidth;
    private final int areaHeight;

    private int currentScrollRow = 0;
    private int columnsPerRow = 0;
    private List<Identifier> currentTextures = new ArrayList<>();
    private ButtonWidget scrollUpButton;
    private ButtonWidget scrollDownButton;

    // Layout constants for the previews
    private static final int PREVIEW_SIZE = 64;
    private static final int PREVIEW_SPACING = 16;
    private static final int PADDING = 10; // padding on left/right sides
    private static final int SPACING_BETWEEN_PREVIEWS = 2;

    public TexturePreviewUI(RepalModMenu.MergedConfigScreen parent, MinecraftClient client, int areaX, int areaY, int areaWidth, int areaHeight) {
        this.parent = parent;
        this.client = client;
        this.areaX = areaX;
        this.areaY = areaY;
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;
    }

    /**
     * Initializes the preview UI: loads textures, computes layout values, and creates the scroll buttons.
     */
    public void init() {
        // Load textures from the resource manager
        TextureManager.loadTextures(client.getResourceManager());

        // Calculate the number of columns based on available width
        int availableWidth = areaWidth - 2 * PADDING;
        int totalPreviewWidth = PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS;
        columnsPerRow = Math.max(1, availableWidth / (totalPreviewWidth + PREVIEW_SPACING));

        updateTextureList();
        initializeScrollButtons();
    }

    /**
     * Updates the list of textures to preview based on the active layer.
     * Also resets the scrolling state and updates the scroll buttons.
     */
    public void updateTextureList() {
        Repal.LOGGER.debug("Starting updateTextureList");

        LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
        Repal.LOGGER.debug("Active layer: {}", activeLayer != null ? activeLayer.getName() : "null");

        if (currentTextures == null) {
            currentTextures = new ArrayList<>();
        } else {
            currentTextures.clear();
        }

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

        // Remove any null textures
        currentTextures.removeIf(texture -> {
            if (texture == null) {
                Repal.LOGGER.warn("Removed null texture from list");
                return true;
            }
            return false;
        });

        // Reset scroll position
        currentScrollRow = 0;

        // Update scroll buttons (if already initialized)
        if (scrollUpButton != null && scrollDownButton != null) {
            scrollUpButton.active = false;
            int visibleRows = (areaHeight - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
            int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
            scrollDownButton.active = totalRows > visibleRows;

            Repal.LOGGER.debug("Scroll state - visibleRows: {}, totalRows: {}, canScrollDown: {}",
                    visibleRows, totalRows, scrollDownButton.active);
        } else {
            Repal.LOGGER.warn("Scroll buttons not initialized");
        }

        Repal.LOGGER.info("Texture list update complete. Total textures: {}", currentTextures.size());
    }

    /**
     * Creates and adds the scroll up/down buttons to the parent screen.
     */
    private void initializeScrollButtons() {
        int buttonWidth = 60;
        // Position scroll buttons at the horizontal center above the preview area
        int centerX = areaX + areaWidth / 2;
        int buttonY = areaY - 20;

        scrollUpButton = ButtonWidget.builder(
                        Text.literal("▲"),
                        button -> {
                            if (currentScrollRow > 0) {
                                currentScrollRow--;
                            }
                        }
                ).dimensions(centerX - buttonWidth - 5, buttonY, buttonWidth, 20)
                .build();

        scrollDownButton = ButtonWidget.builder(
                        Text.literal("▼"),
                        button -> {
                            int visibleRows = (areaHeight - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
                            int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
                            if (currentScrollRow < totalRows - visibleRows) {
                                currentScrollRow++;
                            }
                        }
                ).dimensions(centerX + 5, buttonY, buttonWidth, 20)
                .build();

        parent.addDrawableC(scrollUpButton);
        parent.addDrawableC(scrollDownButton);
    }

    /**
     * Renders all texture previews (both original and processed) along with highlights, labels, and tooltips.
     */
    public void render(DrawContext context, int mouseX, int mouseY) {
        if (currentTextures == null || currentTextures.isEmpty()) {
            Repal.LOGGER.debug("No textures to render");
            return;
        }

        LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
        if (activeLayer == null) {
            Repal.LOGGER.warn("No active layer available for preview rendering");
            return;
        }

        int availableWidth = areaWidth - 2 * PADDING;
        int totalPreviewWidth = PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS;
        columnsPerRow = Math.max(1, availableWidth / (totalPreviewWidth + PREVIEW_SPACING));

        int x = areaX + PADDING;
        int y = areaY;

        int visibleRows = (areaHeight - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
        int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
        int startIndex = currentScrollRow * columnsPerRow;
        int endIndex = Math.min(startIndex + (visibleRows * columnsPerRow), currentTextures.size());

        // Update scroll buttons based on the current scroll state
        scrollUpButton.active = currentScrollRow > 0;
        scrollDownButton.active = currentScrollRow < totalRows - visibleRows;

        for (int i = startIndex; i < endIndex; i++) {
            if (i >= currentTextures.size()) break;

            Identifier texture = currentTextures.get(i);
            int col = (i - startIndex) % columnsPerRow;
            int row = (i - startIndex) / columnsPerRow;

            int baseX = x + col * (totalPreviewWidth + PREVIEW_SPACING);
            int previewY = y + row * (PREVIEW_SIZE + PREVIEW_SPACING);

            try {
                // Draw a yellow highlight if the texture is selected
                if (TextureManager.isSelected(texture)) {
                    context.fill(
                            baseX - 2,
                            previewY - 2,
                            baseX + totalPreviewWidth + 2,
                            previewY + PREVIEW_SIZE + 2,
                            0xFFFFFF00
                    );
                }

                // Draw the original texture preview
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

                // Draw the processed texture preview (or a red-tinted fallback)
                try {
                    Identifier processedTexture = ProcessedTextureCache.getProcessedTexture(texture, activeLayer);
                    if (processedTexture != null) {
                        context.drawTexture(
                                processedTexture,
                                baseX + PREVIEW_SIZE + SPACING_BETWEEN_PREVIEWS,
                                previewY,
                                0, 0,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE
                        );
                    } else {
                        context.drawTexture(
                                texture,
                                baseX + PREVIEW_SIZE + SPACING_BETWEEN_PREVIEWS,
                                previewY,
                                0, 0,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE,
                                PREVIEW_SIZE
                        );
                        context.fill(
                                baseX + PREVIEW_SIZE + SPACING_BETWEEN_PREVIEWS,
                                previewY,
                                baseX + PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS,
                                previewY + PREVIEW_SIZE,
                                0x40FF0000
                        );
                    }
                } catch (Exception e) {
                    Repal.LOGGER.error("Failed to render processed texture for " + texture, e);
                    context.fill(
                            baseX + PREVIEW_SIZE + SPACING_BETWEEN_PREVIEWS,
                            previewY,
                            baseX + PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS,
                            previewY + PREVIEW_SIZE,
                            0x80FF0000
                    );
                }

                // Draw the texture name centered below the previews
                String textureName = texture.getPath().substring(
                        texture.getPath().lastIndexOf('/') + 1,
                        texture.getPath().lastIndexOf('.')
                );
                int textWidth = client.textRenderer.getWidth(textureName);
                int textX = baseX + (totalPreviewWidth / 2) - (textWidth / 2);
                context.drawTextWithShadow(
                        client.textRenderer,
                        textureName,
                        textX,
                        previewY + PREVIEW_SIZE + 2,
                        0xFFFFFF
                );

                // If the mouse hovers over this preview, draw a tooltip
                if (mouseX >= baseX && mouseX < baseX + totalPreviewWidth &&
                        mouseY >= previewY && mouseY < previewY + PREVIEW_SIZE) {

                    List<Text> tooltip = new ArrayList<>();
                    tooltip.add(Text.literal(textureName));

                    if (mouseX >= baseX + PREVIEW_SIZE + SPACING_BETWEEN_PREVIEWS) {
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

    /**
     * Handles mouse clicks within the preview area.
     * If a preview was clicked, toggles its selection.
     *
     * @return true if the click was handled by the preview UI.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if the click is within the preview area boundaries
        if (mouseX >= areaX && mouseX < areaX + areaWidth && mouseY >= areaY && mouseY < areaY + areaHeight) {
            int totalPreviewWidth = PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS;
            int col = (int)((mouseX - (areaX + PADDING)) / (totalPreviewWidth + PREVIEW_SPACING));
            int row = (int)((mouseY - areaY) / (PREVIEW_SIZE + PREVIEW_SPACING));
            int index = (currentScrollRow + row) * columnsPerRow + col;

            if (col >= 0 && col < columnsPerRow && index >= 0 && index < currentTextures.size()) {
                Identifier clickedTexture = currentTextures.get(index);
                TextureManager.toggleTextureSelection(clickedTexture);
                return true;
            }
        }
        return false;
    }

    /**
     * Handles mouse scroll events within the preview area to adjust the scroll row.
     *
     * @return true if the scroll event was handled.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= areaY && mouseY < areaY + areaHeight) {
            int visibleRows = (areaHeight - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
            int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);

            if (verticalAmount > 0 && currentScrollRow > 0) {
                currentScrollRow--;
                return true;
            } else if (verticalAmount < 0 && currentScrollRow < totalRows - visibleRows) {
                currentScrollRow++;
                return true;
            }
        }
        return false;
    }
}
