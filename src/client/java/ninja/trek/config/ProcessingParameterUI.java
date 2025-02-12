package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ninja.trek.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessingParameterUI {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private final RepalModMenu.MergedConfigScreen parent;
    private ContrastSlider contrastSlider;
    private SaturationSlider saturationSlider;
    private HueSlider hueSlider;  // New hue slider
    private PaletteDropdown paletteDropdown;
    private List<String> availablePalettes;
    private int currentPaletteIndex = 0;

    private ProcessingMethod currentMethod = ProcessingMethod.PAL;
    private ButtonWidget methodButton;
    private TextFieldWidget colorsField;

    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 4;

    private enum ProcessingMethod {
        PAL,
        QUANTIZE;

        public ProcessingMethod next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public ProcessingParameterUI(RepalModMenu.MergedConfigScreen parent, MinecraftClient client, int x, int y, int width) {
        this.parent = parent;
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.availablePalettes = new ArrayList<>();
    }

    private class ContrastSlider extends SliderWidget {
        public ContrastSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height,
                    Text.literal("Contrast: " + initialValue),
                    (initialValue - Repal.MIN_ADJUSTMENT) / (float)(Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Contrast: " + getValue()));
        }

        @Override
        protected void applyValue() {
            int value = getValue();
            LayerInfo layer = LayerManager.getInstance().getActiveLayer();
            if (layer != null) {
                layer.setContrast(value);
                ProcessedTextureCache.clearCache();
            }
        }

        public int getValue() {
            return (int)(Repal.MIN_ADJUSTMENT + (Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT) * this.value);
        }

        public void forceValue(int newValue) {
            this.value = (newValue - Repal.MIN_ADJUSTMENT) / (float)(Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT);
            this.updateMessage();
        }
    }

    private class SaturationSlider extends SliderWidget {
        public SaturationSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height,
                    Text.literal("Saturation: " + initialValue),
                    (initialValue - Repal.MIN_ADJUSTMENT) / (float)(Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Saturation: " + getValue()));
        }

        @Override
        protected void applyValue() {
            int value = getValue();
            LayerInfo layer = LayerManager.getInstance().getActiveLayer();
            if (layer != null) {
                layer.setSaturation(value);
                ProcessedTextureCache.clearCache();
            }
        }

        public int getValue() {
            return (int)(Repal.MIN_ADJUSTMENT + (Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT) * this.value);
        }

        public void forceValue(int newValue) {
            this.value = (newValue - Repal.MIN_ADJUSTMENT) / (float)(Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT);
            this.updateMessage();
        }
    }

    private class HueSlider extends SliderWidget {
        public HueSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height,
                    Text.literal("Hue: " + initialValue),
                    (initialValue - Repal.MIN_ADJUSTMENT) / (float)(Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Hue: " + getValue()));
        }

        @Override
        protected void applyValue() {
            int value = getValue();
            LayerInfo layer = LayerManager.getInstance().getActiveLayer();
            if (layer != null) {
                layer.setHue(value);
                ProcessedTextureCache.clearCache();
            }
        }

        public int getValue() {
            return (int)(Repal.MIN_ADJUSTMENT + (Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT) * this.value);
        }

        public void forceValue(int newValue) {
            this.value = (newValue - Repal.MIN_ADJUSTMENT) / (float)(Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT);
            this.updateMessage();
        }
    }

    /**
     * This inner class manages a list of up to 5 buttons.
     * Each button’s text is the name of a palette file.
     * Clicking a button selects that palette.
     * Scrolling over any button shifts the list.
     */
    private class PaletteDropdown {
        private int x, y, width, buttonHeight;
        // The “offset” is the index into availablePalettes corresponding to the first button.
        private int offset = 0;
        private final int maxButtons = 5;
        private List<PaletteButton> buttons = new ArrayList<>();

        public PaletteDropdown(MinecraftClient client, int x, int y, int width, int buttonHeight, TexturePreviewUI texturePreviewUI) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.buttonHeight = buttonHeight;
            // Create one button for each line, up to maxButtons (or fewer if there are not many palettes).
            int numButtons = Math.min(maxButtons, availablePalettes.size());
            for (int i = 0; i < numButtons; i++) {
                int buttonY = y + i * (buttonHeight + SPACING);
                PaletteButton button = new PaletteButton(x, buttonY, width, buttonHeight, i, texturePreviewUI);
                buttons.add(button);
            }
            updateButtonLabels();
        }

        /**
         * Sets the offset into the list of palettes and updates all button labels.
         */
        public void setOffset(int newOffset) {
            offset = newOffset;
            updateButtonLabels();
        }

        /**
         * Updates the text on each button to show the palette name corresponding to its (offset + index).
         */
        public void updateButtonLabels() {
            for (int i = 0; i < buttons.size(); i++) {
                int paletteIndex = offset + i;
                if (paletteIndex < availablePalettes.size()) {
                    buttons.get(i).setMessage(Text.literal(availablePalettes.get(paletteIndex)));
                } else {
                    buttons.get(i).setMessage(Text.literal("")); // In case there’s no palette.
                }
            }
        }

        /**
         * Scrolls one “up” (if possible).
         */
        public void scrollUp() {
            if (offset > 0) {
                offset--;
                updateButtonLabels();
            }
        }

        /**
         * Scrolls one “down” (if possible).
         */
        public void scrollDown() {
            if (offset < availablePalettes.size() - buttons.size()) {
                offset++;
                updateButtonLabels();
            }
        }

        public List<PaletteButton> getButtons() {
            return buttons;
        }

        /**
         * A single button in the palette dropdown.
         * Clicking the button sets the active palette.
         * Also, this button intercepts mouse scroll events.
         */
        private class PaletteButton extends ButtonWidget {
            // buttonIndex is the button’s position within the dropdown (0..buttons.size()-1)
            private final int buttonIndex;

            public PaletteButton(int x, int y, int width, int height, int buttonIndex, TexturePreviewUI texturePreviewUI) {
                super(x, y, width, height, Text.literal(""), // Inside PaletteDropdown.PaletteButton’s constructor lambda:
                        button -> {
                            int paletteIndex = offset + buttonIndex;
                            if (paletteIndex < availablePalettes.size()) {
                                String selected = availablePalettes.get(paletteIndex);
                                LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
                                if (activeLayer != null) {
                                    activeLayer.setPalette(selected);
                                    ImageProcessor.clearCache();  // ← Clears the old color→color mappings
                                    // Clear the entire processed texture cache
                                    ProcessedTextureCache.clearCache();
                                    // Optionally clear just the active layer’s cache if you prefer:
                                     ProcessedTextureCache.clearLayerCache(activeLayer.getId());
                                    // Instead of just reprocessing the current preview, force a full update:
                                    MinecraftClient.getInstance().execute(() -> {
                                        texturePreviewUI.updateTextureList();
                                    });
                                }
                            }


            }, (in) -> Text.literal("Palette selection button for palette index " + (offset + buttonIndex))
                );
                this.buttonIndex = buttonIndex;
            }

            @Override
            public boolean mouseScrolled(double mouseX, double mouseY, double horiz, double vert) {
                if (isMouseOver(mouseX, mouseY)) {
                    if (vert > 0) {
                        scrollUp();
                    } else if (vert < 0) {
                        scrollDown();
                    }
                    return true;
                }
                return super.mouseScrolled(mouseX, mouseY, horiz, vert);
            }
        }
    }

    public void init(TexturePreviewUI texturePreviewUI) {
        LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
        if (activeLayer == null) return;

        int currentY = y;

        // Create method button
        methodButton = ButtonWidget.builder(
                        Text.literal("Method: " + currentMethod),
                        this::onMethodButtonClick)
                .dimensions(x, currentY, width, WIDGET_HEIGHT)
                .build();
        parent.addDrawableC(methodButton);
        currentY += WIDGET_HEIGHT + SPACING;

        // Create colors text field
        colorsField = new TextFieldWidget(
                client.textRenderer,
                x,
                currentY,
                width,
                WIDGET_HEIGHT,
                Text.empty()
        );
        colorsField.setPlaceholder(Text.literal("Colors (default: 256)"));
        colorsField.setText("256");
        colorsField.setTextPredicate(str -> str.matches("\\d*")); // Only allow numbers
        parent.addDrawableC(colorsField);
        currentY += WIDGET_HEIGHT + SPACING;

        // Create contrast slider
        contrastSlider = new ContrastSlider(x, currentY, width, WIDGET_HEIGHT, activeLayer.getContrast());
        currentY += WIDGET_HEIGHT + SPACING;

        // Create saturation slider
        saturationSlider = new SaturationSlider(x, currentY, width, WIDGET_HEIGHT, activeLayer.getSaturation());
        currentY += WIDGET_HEIGHT + SPACING;

        // Create hue slider
        hueSlider = new HueSlider(x, currentY, width, WIDGET_HEIGHT, activeLayer.getHue());
        currentY += WIDGET_HEIGHT + SPACING;

        // Get available palette names and create dropdown
        availablePalettes = RepalResourceReloadListener.getAvailablePalettes()
                .stream()
                .map(PaletteInfo::getName)
                .collect(Collectors.toList());

        // Determine the index of the active palette
        String currentPalette = activeLayer.getPalette();
        currentPaletteIndex = availablePalettes.indexOf(currentPalette);
        if (currentPaletteIndex == -1) currentPaletteIndex = 0;

        // Create the palette dropdown with updated Y position
        paletteDropdown = new PaletteDropdown(client, x, currentY, width, WIDGET_HEIGHT, texturePreviewUI);

        // Add all UI elements to parent
        parent.addDrawableC(contrastSlider);
        parent.addDrawableC(saturationSlider);
        parent.addDrawableC(hueSlider);

        // Add palette buttons
        for (ButtonWidget btn : paletteDropdown.getButtons()) {
            parent.addDrawableC(btn);
        }
    }

    private void onMethodButtonClick(ButtonWidget button) {
        currentMethod = currentMethod.next();
        button.setMessage(Text.literal("Method: " + currentMethod));
    }

    public ProcessingMethod getCurrentMethod() {
        return currentMethod;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return colorsField.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char chr, int modifiers) {
        return colorsField.charTyped(chr, modifiers);
    }

    public int getColorsCount() {
        try {
            return Integer.parseInt(colorsField.getText());
        } catch (NumberFormatException e) {
            return 256; // Default value
        }
    }

    public void updateValues(LayerInfo layer) {
        if (layer == null) return;
        contrastSlider.forceValue(layer.getContrast());
        saturationSlider.forceValue(layer.getSaturation());
        hueSlider.forceValue(layer.getHue());

        String currentPalette = layer.getPalette();
        currentPaletteIndex = availablePalettes.indexOf(currentPalette);
        if (currentPaletteIndex == -1) currentPaletteIndex = 0;

        int numButtons = paletteDropdown.getButtons().size();
        if (currentPaletteIndex < paletteDropdown.offset) {
            paletteDropdown.setOffset(currentPaletteIndex);
        } else if (currentPaletteIndex >= paletteDropdown.offset + numButtons) {
            paletteDropdown.setOffset(currentPaletteIndex - numButtons + 1);
        }
    }

    public void tick() {
        // Nothing required here
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

}