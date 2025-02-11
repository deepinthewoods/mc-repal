package ninja.trek.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ninja.trek.TextureManager;
import ninja.trek.Repal;
import java.util.ArrayList;
import java.util.List;

public class TextureComboBox extends TextFieldWidget {
    private boolean isDropdownVisible = false;
    private List<Identifier> suggestions = new ArrayList<>();
    private int selectedSuggestion = -1;
    private final int dropdownHeight = 120;
    private static final int SUGGESTION_HEIGHT = 12;
    private final MinecraftClient client;

    public TextureComboBox(MinecraftClient client, int x, int y, int width) {
        super(client.textRenderer, x, y, width, 20, Text.empty());
        this.client = client;
        setPlaceholder(Text.translatable("repal.search.textures"));
        setMaxLength(50);
        setEditable(true);
        setVisible(true);
        setFocused(true);
        active = true;
        isDropdownVisible = true;
        suggestions = TextureManager.searchTextures("");

        // Set up text change callback
        setChangedListener(this::onTextChanged);

        Repal.LOGGER.info("TextureComboBox initialized with active state: " + active);
    }

    private void onTextChanged(String newText) {
        Repal.LOGGER.info("Text changed to: " + newText);
        updateSuggestions();
        // Reset selection when text changes
        selectedSuggestion = -1;
        isDropdownVisible = true;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isActive() || !isFocused()) {
            return false;
        }

        Repal.LOGGER.info("Character typed: " + chr);
        String currentText = getText();
        setText(currentText + chr);
        updateSuggestions();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive()) {
            return false;
        }

        Repal.LOGGER.info("Key pressed: " + keyCode);

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

    private void updateSuggestions() {
        String currentText = getText();
        Repal.LOGGER.info("Updating suggestions for text: '" + currentText + "'");
        suggestions = TextureManager.searchTextures(currentText);
        isDropdownVisible = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive()) {
            return false;
        }

        Repal.LOGGER.info("Mouse clicked at: " + mouseX + ", " + mouseY);

        // Check dropdown clicks first
        if (isDropdownVisible) {
            int relativeY = (int)(mouseY - (getY() + getHeight()));
            if (mouseX >= getX() && mouseX < getX() + getWidth() &&
                    relativeY >= 0 && relativeY < Math.min(suggestions.size() * SUGGESTION_HEIGHT, dropdownHeight)) {
                int index = relativeY / SUGGESTION_HEIGHT;
                if (index < suggestions.size()) {
                    selectSuggestion(index);
                    return true;
                }
            }
        }

        // Handle text field clicks
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (result) {
            setFocused(true);
            isDropdownVisible = true;
            updateSuggestions();
        }
        return result;
    }

    private void selectSuggestion(int index) {
        if (index >= 0 && index < suggestions.size()) {
            Identifier selected = suggestions.get(index);
            String newText = selected.getPath().substring(
                    selected.getPath().lastIndexOf('/') + 1,
                    selected.getPath().lastIndexOf('.')
            );
            setText(newText);
            TextureManager.setCurrentPreviewTexture(selected);
            setFocused(false);
            isDropdownVisible = false;
        }
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) {
            return;
        }

        super.renderWidget(context, mouseX, mouseY, delta);

        if (isDropdownVisible && !suggestions.isEmpty()) {
            int x = getX();
            int y = getY() + getHeight();
            int width = this.width;

            // Draw dropdown background
            context.fill(x, y, x + width, y + Math.min(suggestions.size() * SUGGESTION_HEIGHT, dropdownHeight), 0xFF000000);

            // Draw suggestions
            int drawnCount = 0;
            for (int i = 0; i < suggestions.size() && drawnCount * SUGGESTION_HEIGHT < dropdownHeight; i++) {
                Identifier suggestion = suggestions.get(i);
                String displayText = suggestion.getPath().substring(
                        suggestion.getPath().lastIndexOf('/') + 1,
                        suggestion.getPath().lastIndexOf('.')
                );
                int suggestionY = y + (drawnCount * SUGGESTION_HEIGHT);
                int textColor = (i == selectedSuggestion) ? 0xFFFFFF00 : 0xFFFFFFFF;

                // Highlight if mouse is over
                if (mouseX >= x && mouseX < x + width &&
                        mouseY >= suggestionY && mouseY < suggestionY + SUGGESTION_HEIGHT) {
                    context.fill(x, suggestionY, x + width, suggestionY + SUGGESTION_HEIGHT, 0xFF404040);
                }
                // Highlight if selected
                else if (i == selectedSuggestion) {
                    context.fill(x, suggestionY, x + width, suggestionY + SUGGESTION_HEIGHT, 0xFF303030);
                }

                context.drawTextWithShadow(client.textRenderer,
                        displayText, x + 2, suggestionY + 2, textColor);
                drawnCount++;
            }
        }
    }

    @Override
    public void write(String text) {
        super.write(text);
        updateSuggestions();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            isDropdownVisible = true;
            updateSuggestions();
        }
    }
}