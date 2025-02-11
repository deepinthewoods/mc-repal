package ninja.trek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.Identifier;

import java.util.*;

public class LayerManager {
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

    private final Map<UUID, LayerInfo> layers;
    private UUID activeLayer;
    private static LayerManager INSTANCE;

    private LayerManager() {
        this.layers = new HashMap<>();
        // Create default layer
        LayerInfo defaultLayer = new LayerInfo("Default");
        this.layers.put(defaultLayer.getId(), defaultLayer);
        this.activeLayer = defaultLayer.getId();
    }

    public static LayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LayerManager();
        }
        return INSTANCE;
    }

    // Layer operations
    public LayerInfo createLayer(String name) {
        LayerInfo layer = new LayerInfo(name);
        layers.put(layer.getId(), layer);
        return layer;
    }

    public void deleteLayer(UUID id) {
        if (layers.size() <= 1) {
            Repal.LOGGER.warn("Cannot delete the last remaining layer");
            return;
        }
        layers.remove(id);
        if (activeLayer.equals(id)) {
            activeLayer = layers.keySet().iterator().next();
        }
    }

    public void moveTexturesToLayer(Collection<Identifier> textures, UUID targetLayer) {
        LayerInfo layer = layers.get(targetLayer);
        if (layer != null) {
            // Remove textures from all other layers first
            for (LayerInfo otherLayer : layers.values()) {
                otherLayer.getTextures().removeAll(textures);
            }
            // Add to target layer
            layer.addTextures(textures);
        }
    }

    // Getters
    public LayerInfo getLayer(UUID id) {
        return layers.get(id);
    }

    public LayerInfo getActiveLayer() {
        return layers.get(activeLayer);
    }

    public List<LayerInfo> getAllLayers() {
        return new ArrayList<>(layers.values());
    }

    public void setActiveLayer(UUID id) {
        if (layers.containsKey(id)) {
            this.activeLayer = id;
        }
    }

    // Import/Export
    public String exportToJson() {
        return GSON.toJson(new ArrayList<>(layers.values()));
    }

    public void importFromJson(String json) {
        try {
            LayerInfo[] importedLayers = GSON.fromJson(json, LayerInfo[].class);
            layers.clear();

            for (LayerInfo layer : importedLayers) {
                layers.put(layer.getId(), layer);
            }

            if (layers.isEmpty()) {
                // Create default layer if import was empty
                LayerInfo defaultLayer = new LayerInfo("Default");
                layers.put(defaultLayer.getId(), defaultLayer);
                activeLayer = defaultLayer.getId();
            } else if (!layers.containsKey(activeLayer)) {
                // Set active layer to first available if current one was removed
                activeLayer = layers.keySet().iterator().next();
            }
        } catch (Exception e) {
            Repal.LOGGER.error("Failed to import layers", e);
            // Reset to default state
            layers.clear();
            LayerInfo defaultLayer = new LayerInfo("Default");
            layers.put(defaultLayer.getId(), defaultLayer);
            activeLayer = defaultLayer.getId();
        }
    }

    // Clear everything
    public void reset() {
        layers.clear();
        LayerInfo defaultLayer = new LayerInfo("Default");
        layers.put(defaultLayer.getId(), defaultLayer);
        activeLayer = defaultLayer.getId();
    }
}
