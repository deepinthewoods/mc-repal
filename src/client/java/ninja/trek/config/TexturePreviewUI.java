package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
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
import java.util.UUID;
import java.util.stream.Collectors;

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
    private UUID currentLayerId;

    // Layout constants for the previews
    private static final int PREVIEW_SIZE = 64;
    private static final int PREVIEW_SPACING = 16;
    private static final int PADDING = 10;
    private static final int SPACING_BETWEEN_PREVIEWS = 2;

    public TexturePreviewUI(RepalModMenu.MergedConfigScreen parent, MinecraftClient client, int areaX, int areaY, int areaWidth, int areaHeight) {
        this.parent = parent;
        this.client = client;
        this.areaX = areaX;
        this.areaY = areaY;
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;

        // Initialize with the current active layer
        LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
        if (activeLayer != null) {
            this.currentLayerId = activeLayer.getId();
        }
    }

    public void init() {
        TextureManager.loadTextures(client.getResourceManager());

        int availableWidth = areaWidth - 2 * PADDING;
        int totalPreviewWidth = PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS;
        columnsPerRow = Math.max(1, availableWidth / (totalPreviewWidth + PREVIEW_SPACING));

        updateTextureList();
        initializeScrollButtons();
    }

    public void setCurrentLayer(UUID layerId) {
        if (this.currentLayerId != layerId) {
            this.currentLayerId = layerId;
            currentScrollRow = 0; // Reset scroll position when changing layers
            updateTextureList();
        }
    }

    public void updateTextureList() {
        Repal.LOGGER.debug("Starting updateTextureList");
        if (currentTextures == null) {
            currentTextures = new ArrayList<>();
        } else {
            currentTextures.clear();
        }

        // Only show textures for the current layer
        LayerInfo layer = LayerManager.getInstance().getLayer(currentLayerId);
        if (layer != null) {
            Set<Identifier> layerTextures = layer.getTextures();
            if (layerTextures != null && !layerTextures.isEmpty()) {
                currentTextures.addAll(layerTextures);
                Repal.LOGGER.info("Updated texture list for layer '{}': {} textures",
                        layer.getName(), currentTextures.size());
            }
        }

        currentTextures.removeIf(texture -> {
            if (texture == null) {
                Repal.LOGGER.warn("Removed null texture from list");
                return true;
            }
            return false;
        });

        // Reset scroll position and update buttons
        currentScrollRow = 0;
        updateScrollButtons();
    }

    private void updateScrollButtons() {
        if (scrollUpButton != null && scrollDownButton != null) {
            scrollUpButton.active = currentScrollRow > 0;
            int visibleRows = (areaHeight - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
            int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
            scrollDownButton.active = totalRows > visibleRows && currentScrollRow < totalRows - visibleRows;
        }
    }

    private void initializeScrollButtons() {
        int buttonWidth = 60;
        int centerX = areaX + areaWidth / 2;
        int buttonY = areaY - 20;

        scrollUpButton = ButtonWidget.builder(
                        Text.literal("▲"),
                        button -> {
                            if (currentScrollRow > 0) {
                                currentScrollRow--;
                                updateScrollButtons();
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
                                updateScrollButtons();
                            }
                        }
                ).dimensions(centerX + 5, buttonY, buttonWidth, 20)
                .build();

        parent.addDrawableC(scrollUpButton);
        parent.addDrawableC(scrollDownButton);
        updateScrollButtons();
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        LayerInfo currentLayer = LayerManager.getInstance().getLayer(currentLayerId);
        if (currentLayer == null) {
            Repal.LOGGER.warn("No layer available for preview rendering");
            return;
        }

        // Only show textures that belong to the current layer
        if (currentTextures == null || !currentTextures.stream().anyMatch(texture -> currentLayer.getTextures().contains(texture))) {
            // Render "No textures in this layer" message
            String message = "No textures in this layer";
            int textWidth = client.textRenderer.getWidth(message);
            int centerX = areaX + (areaWidth - textWidth) / 2;
            int centerY = areaY + areaHeight / 2;
            context.drawTextWithShadow(
                    client.textRenderer,
                    message,
                    centerX,
                    centerY,
                    0x888888
            );
            return;
        }

        renderTexturesGrid(context, mouseX, mouseY, currentLayer);
    }

    private void renderTexturesGrid(DrawContext context, int mouseX, int mouseY, LayerInfo layer) {
        // Filter textures to only show ones from the current layer
        List<Identifier> layerTextures = currentTextures.stream()
                .filter(texture -> layer.getTextures().contains(texture))
                .collect(Collectors.toList());

        // Calculate layout dimensions
        int availableWidth = areaWidth - 2 * PADDING;
        int totalPreviewWidth = PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS;
        columnsPerRow = Math.max(1, availableWidth / (totalPreviewWidth + PREVIEW_SPACING));

        // Calculate starting positions
        int x = areaX + PADDING;
        int y = areaY;

        // Calculate visible area
        int visibleRows = (areaHeight - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
        int startIndex = currentScrollRow * columnsPerRow;
        int endIndex = Math.min(startIndex + (visibleRows * columnsPerRow), layerTextures.size());

        // Render each texture in the grid
        for (int i = startIndex; i < endIndex; i++) {
            if (i >= layerTextures.size()) break;

            Identifier texture = layerTextures.get(i);
            int col = (i - startIndex) % columnsPerRow;
            int row = (i - startIndex) / columnsPerRow;

            // Calculate position for this texture preview
            int baseX = x + col * (totalPreviewWidth + PREVIEW_SPACING);
            int previewY = y + row * (PREVIEW_SIZE + PREVIEW_SPACING);

            // Render the texture preview with all its components
            renderTexturePreview(
                    context,
                    mouseX,
                    mouseY,
                    texture,
                    layer,
                    baseX,
                    previewY,
                    totalPreviewWidth
            );
        }

        // Update scroll buttons after rendering
        updateScrollButtons();
    }

    private void renderTexturePreview(DrawContext context, int mouseX, int mouseY,
                                      Identifier texture, LayerInfo layer, int baseX, int previewY, int totalPreviewWidth) {
        try {
            // Draw selection highlight
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

            // Draw processed texture
            renderProcessedTexture(context, texture, layer, baseX, previewY);

            // Draw texture name
            renderTextureName(context, texture, baseX, previewY, totalPreviewWidth);

            // Draw tooltip if mouse is hovering
            if (mouseX >= baseX && mouseX < baseX + totalPreviewWidth &&
                    mouseY >= previewY && mouseY < previewY + PREVIEW_SIZE) {
                renderTooltip(context, texture, layer, mouseX, mouseY, baseX, PREVIEW_SIZE);
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

    private void renderProcessedTexture(DrawContext context, Identifier texture,
                                        LayerInfo layer, int baseX, int previewY) {
        try {
            Identifier processedTexture = ProcessedTextureCache.getProcessedTexture(texture, layer);
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
            Repal.LOGGER.error("Failed to render processed texture", e);
            context.fill(
                    baseX + PREVIEW_SIZE + SPACING_BETWEEN_PREVIEWS,
                    previewY,
                    baseX + PREVIEW_SIZE * 2 + SPACING_BETWEEN_PREVIEWS,
                    previewY + PREVIEW_SIZE,
                    0x80FF0000
            );
        }
    }

    private void renderTextureName(DrawContext context, Identifier texture,
                                   int baseX, int previewY, int totalPreviewWidth) {
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
    }

    private void renderTooltip(DrawContext context, Identifier texture, LayerInfo layer,
                               int mouseX, int mouseY, int baseX, int previewWidth) {
        List<Text> tooltip = new ArrayList<>();
        tooltip.add(Text.literal(texture.getPath()));

        if (mouseX >= baseX + previewWidth + SPACING_BETWEEN_PREVIEWS) {
            tooltip.add(Text.literal("Contrast: " + layer.getContrast())
                    .setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            tooltip.add(Text.literal("Saturation: " + layer.getSaturation())
                    .setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            tooltip.add(Text.literal("Palette: " + layer.getPalette())
                    .setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
        }

        context.drawTooltip(
                client.textRenderer,
                tooltip,
                mouseX,
                mouseY
        );
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= areaX && mouseX < areaX + areaWidth &&
                mouseY >= areaY && mouseY < areaY + areaHeight) {

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

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= areaY && mouseY < areaY + areaHeight) {
            int visibleRows = (areaHeight - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
            int totalRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);

            if (verticalAmount > 0 && currentScrollRow > 0) {
                currentScrollRow--;
                updateScrollButtons();
                return true;
            } else if (verticalAmount < 0 && currentScrollRow < totalRows - visibleRows) {
                currentScrollRow++;
                updateScrollButtons();
                return true;
            }
        }
        return false;
    }
}