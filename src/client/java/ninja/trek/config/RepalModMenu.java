package ninja.trek.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ninja.trek.*;

import java.util.ArrayList;
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

        public MergedConfigScreen(Screen parent) {
            super(Text.translatable("repal.config.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
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

            // Load textures
            TextureManager.loadTextures(client.getResourceManager());
            updateTextureList();

            // Add all UI elements to drawable children
            addDrawableChildren();
        }

        private void initializeConfigUI(int x, int y, int width) {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(this)
                    .setTitle(Text.translatable("repal.config.title"))
                    .setSavingRunnable(() -> {
                        RepalConfig.save();
                        ProcessedTextureCache.clearCache();
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
                var contrastEntry = entryBuilder.startIntSlider(
                                Text.translatable("repal.config.contrast"),
                                activeLayer.getContrast(),
                                Repal.MIN_ADJUSTMENT,
                                Repal.MAX_ADJUSTMENT
                        )
                        .setDefaultValue(0)
                        .setTooltip(Text.translatable("repal.tooltip.contrast"))
                        .setSaveConsumer(activeLayer::setContrast)
                        .build();
                general.addEntry(contrastEntry);

                // Saturation Slider
                var saturationEntry = entryBuilder.startIntSlider(
                                Text.translatable("repal.config.saturation"),
                                activeLayer.getSaturation(),
                                Repal.MIN_ADJUSTMENT,
                                Repal.MAX_ADJUSTMENT
                        )
                        .setDefaultValue(0)
                        .setTooltip(Text.translatable("repal.tooltip.saturation"))
                        .setSaveConsumer(activeLayer::setSaturation)
                        .build();
                general.addEntry(saturationEntry);

                // Palette Selection
                List<String> availablePaletteNames = RepalResourceReloadListener.getAvailablePalettes()
                        .stream()
                        .map(PaletteInfo::getName)
                        .collect(Collectors.toList());

                if (!availablePaletteNames.isEmpty()) {
                    var paletteEntry = entryBuilder.<String>startDropdownMenu(
                                    Text.translatable("repal.config.palette"),
                                    activeLayer.getPalette(),
                                    s -> s,
                                    s -> Text.literal(s)
                            )
                            .setSelections(availablePaletteNames)
                            .setDefaultValue(availablePaletteNames.get(0))
                            .setTooltip(Text.translatable("repal.tooltip.palette"))
                            .setSaveConsumer(activeLayer::setPalette)
                            .build();
                    general.addEntry(paletteEntry);
                }
            }

            clothConfigScreen = builder.build();
            clothConfigScreen.init(client, width, height);
        }

        private void initializeScrollButtons(int mainWidth) {
            int buttonWidth = 60;
            scrollUpButton = ButtonWidget.builder(
                            Text.literal("▲ Up"),
                            button -> {
                                if (currentScrollRow > 0) currentScrollRow--;
                                updateTextureList();
                            }
                    ).dimensions(mainWidth / 2 - buttonWidth - 5, PREVIEWS_START_Y - 20, buttonWidth, 20)
                    .build();

            scrollDownButton = ButtonWidget.builder(
                            Text.literal("Down ▼"),
                            button -> {
                                int maxRows = (int) Math.ceil((double) currentTextures.size() / columnsPerRow);
                                int visibleRows = (height - PREVIEWS_START_Y - 20) / (PREVIEW_SIZE + PREVIEW_SPACING);
                                if (currentScrollRow < maxRows - visibleRows) {
                                    currentScrollRow++;
                                    updateTextureList();
                                }
                            }
                    ).dimensions(mainWidth / 2 + 5, PREVIEWS_START_Y - 20, buttonWidth, 20)
                    .build();

            addDrawableChild(scrollUpButton);
            addDrawableChild(scrollDownButton);
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
            LayerInfo activeLayer = LayerManager.getInstance().getActiveLayer();
            currentTextures = activeLayer != null ?
                    new ArrayList<>(activeLayer.getTextures()) :
                    TextureManager.getAllBlockTextures();
        }

        private void onProcessClick(ButtonWidget button) {
            TextureProcessor.processAllTextures();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
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
            // Implement texture preview rendering here
            // (Previous implementation remains largely the same)
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (layerUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            if (presetUI.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            if (clothConfigScreen != null && clothConfigScreen.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            if (textureSearch.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
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
            layerUI.tick();
            presetUI.tick();
            textureSearch.tick();
        }
    }
}