package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ninja.trek.LayerInfo;
import ninja.trek.LayerManager;
import ninja.trek.PaletteInfo;
import ninja.trek.ProcessedTextureCache;
import ninja.trek.Repal;
import ninja.trek.RepalResourceReloadListener;
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
    // Instead of a simple button we now use a dropdown text field for palette selection.
    private PaletteDropdown paletteDropdown;
    private List<String> availablePalettes;
    private int currentPaletteIndex = 0;
    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 4;

    // No key or character input is needed for the sliders.
    // Instead, delegate keyboard events only to the palette dropdown.
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (paletteDropdown != null && paletteDropdown.isFocused() && paletteDropdown.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (paletteDropdown != null && paletteDropdown.isFocused() && paletteDropdown.charTyped(chr, modifiers)) {
            return true;
        }
        return false;
    }

    private class ContrastSlider extends SliderWidget {
        public ContrastSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height,
                    Text.literal("Contrast: " + initialValue),
                    (initialValue - Repal.MIN_ADJUSTMENT) / (float) (Repal.MAX_ADJUSTMENT - Repal.MIN_ADJUSTMENT));
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

    // New dropdown widget for palette selection.
    // This text field displays the current palette and shows a dropdown with suggestions
    // (filtered by the entered text) drawn below it.
    private class PaletteDropdown extends TextFieldWidget {
        private boolean isDropdownVisible = false;
        private List<String> suggestions = new ArrayList<>();
        private int selectedSuggestion = -1;
        private final int dropdownHeight = 120;
        private static final int SUGGESTION_HEIGHT = 12;

        public PaletteDropdown(MinecraftClient client, int x, int y, int width, int height, Text text) {
            super(client.textRenderer, x, y, width, height, text);
            setEditable(true);
            setMaxLength(32);
            setPlaceholder(Text.literal("Select palette..."));
            updateSuggestions();
            setChangedListener(this::onTextChanged);
            active = true;
        }

        private void onTextChanged(String newText) {
            updateSuggestions();
            selectedSuggestion = -1;
            isDropdownVisible = true;

        }

        private void updateSuggestions() {
            String currentText = getText().toLowerCase();
            suggestions = availablePalettes.stream()
                    .filter(p -> p.toLowerCase().contains(currentText))
                    .collect(Collectors.toList());
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isActive() || !isFocused()) return false;
            switch (keyCode) {
                case 265: // Up arrow
                    if (selectedSuggestion > 0) {
                        selectedSuggestion--;
                        return true;
                    }
                    break;
                case 264: // Down arrow
                    if (selectedSuggestion < suggestions.size() - 1) {
                        selectedSuggestion++;
                        return true;
                    }
                    break;
                case 257: // Enter
                    if (selectedSuggestion >= 0 && selectedSuggestion < suggestions.size()) {
                        selectSuggestion(selectedSuggestion);
                        return true;
                    }
                    break;
            }
            boolean result = super.keyPressed(keyCode, scanCode, modifiers);
            if (result) {
                updateSuggestions();
            }
            return result;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (!isActive() || !isFocused()) return false;
            boolean handled = super.charTyped(chr, modifiers);
            if (handled) {
                updateSuggestions();
            }
            return handled;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isActive()) {
                return false;
            }
            // If click is within the text field, focus it and show dropdown.
            if (mouseX >= getX() && mouseX < getX() + getWidth() &&
                    mouseY >= getY() && mouseY < getY() + getHeight()) {
                setFocused(true);
                isDropdownVisible = true;
                return super.mouseClicked(mouseX, mouseY, button);
            }
            // Check if click is in the dropdown area.
            int dropdownY = getY() + getHeight();
            if (isDropdownVisible && mouseX >= getX() && mouseX < getX() + getWidth() &&
                    mouseY >= dropdownY && mouseY < dropdownY + Math.min(suggestions.size() * SUGGESTION_HEIGHT, dropdownHeight)) {
                int index = (int)((mouseY - dropdownY) / SUGGESTION_HEIGHT);
                if (index < suggestions.size()) {
                    selectSuggestion(index);
                    return true;
                }
            }
            setFocused(false);
            isDropdownVisible = false;
            return false;
        }
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            int hitboxX = getX();
            int hitboxY = getY();
            int hitboxWidth = getWidth();
            int hitboxHeight = getHeight();
            if (isDropdownVisible) {
                // Extend the hitbox to cover the dropdown suggestions
                hitboxHeight += Math.min(suggestions.size() * SUGGESTION_HEIGHT, dropdownHeight);
            }
            return mouseX >= hitboxX && mouseX < hitboxX + hitboxWidth &&
                    mouseY >= hitboxY && mouseY < hitboxY + hitboxHeight;
        }


        private void selectSuggestion(int index) {
            if (index >= 0 && index < suggestions.size()) {
                String selected = suggestions.get(index);
                setText(selected);
                LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
                if (activeLayer != null) {
                    activeLayer.setPalette(selected);
                    ProcessedTextureCache.clearCache();
                }
                isDropdownVisible = false;
                setFocused(false);
            }
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);
            if (isDropdownVisible && !suggestions.isEmpty()) {
                int x = getX();
                int y = getY() + getHeight();
                int width = this.width;
                context.fill(x, y, x + width, y + Math.min(suggestions.size() * SUGGESTION_HEIGHT, dropdownHeight), 0xFF000000);
                for (int i = 0; i < suggestions.size() && i * SUGGESTION_HEIGHT < dropdownHeight; i++) {
                    int suggestionY = y + i * SUGGESTION_HEIGHT;
                    int textColor = (i == selectedSuggestion) ? 0xFFFFFF00 : 0xFFFFFFFF;
                    context.drawTextWithShadow(client.textRenderer, suggestions.get(i), x + 2, suggestionY + 2, textColor);
                }
            }
        }


        public void tick() {
            if (isDropdownVisible) {
                updateSuggestions();
            }
        }
    }
    public void tick(){
        paletteDropdown.tick();
    }

    public ProcessingParameterUI(RepalModMenu.MergedConfigScreen parent, MinecraftClient client, int x, int y, int width) {
        this.parent = parent;
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.availablePalettes = new ArrayList<>();
    }

    public void init() {
        LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
        if (activeLayer == null) return;
        int currentY = y;
        // Create contrast slider
        contrastSlider = new ContrastSlider(x, currentY, width, WIDGET_HEIGHT, activeLayer.getContrast());
        currentY += WIDGET_HEIGHT + SPACING;
        // Create saturation slider
        saturationSlider = new SaturationSlider(x, currentY, width, WIDGET_HEIGHT, activeLayer.getSaturation());
        currentY += WIDGET_HEIGHT + SPACING;
        // Get available palette names from the resource reload listener.
        availablePalettes = RepalResourceReloadListener.getAvailablePalettes()
                .stream()
                .map(PaletteInfo::getName)
                .collect(Collectors.toList());
        // Determine the index of the active palette.
        String currentPalette = activeLayer.getPalette();
        currentPaletteIndex = availablePalettes.indexOf(currentPalette);
        if (currentPaletteIndex == -1) currentPaletteIndex = 0;
        // Create the palette dropdown.
        paletteDropdown = new PaletteDropdown(client, x, currentY, width, WIDGET_HEIGHT,
                Text.literal(availablePalettes.get(currentPaletteIndex)));
        paletteDropdown.active = true;
        parent.addDrawableC(paletteDropdown);
        // Add placeholders for the slider areas.
        parent.addDrawableC(
                contrastSlider
        );
        parent.addDrawableC(
                saturationSlider
        );
    }



    public void updateValues(LayerInfo layer) {
        if (layer == null) return;
        contrastSlider.forceValue(layer.getContrast());
        saturationSlider.forceValue(layer.getSaturation());
        String currentPalette = layer.getPalette();
        currentPaletteIndex = availablePalettes.indexOf(currentPalette);
        if (currentPaletteIndex == -1) currentPaletteIndex = 0;
        paletteDropdown.setText(availablePalettes.get(currentPaletteIndex));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;

    }
}
