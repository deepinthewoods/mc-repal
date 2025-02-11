package ninja.trek;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextureManager {
    private static final Pattern BLOCK_TEXTURE_PATTERN = Pattern.compile("textures/block/.*\\.png");
    private static final List<Identifier> allBlockTextures = new ArrayList<>();
    private static final Set<Identifier> selectedTextures = new HashSet<>();
    private static Identifier currentPreviewTexture = null;

    private static final Set<String> commonPrefixes = new HashSet<>(Arrays.asList(
            "stone", "dirt", "grass", "planks", "log", "leaves",
            "ore", "wool", "glass", "concrete", "terracotta"
    ));

    public static void loadTextures(ResourceManager resourceManager) {
        allBlockTextures.clear();
        allBlockTextures.addAll(
                resourceManager.findResources("textures/block", id ->
                                BLOCK_TEXTURE_PATTERN.matcher(id.getPath()).matches()
                        ).keySet().stream()
                        .sorted((a, b) -> {
                            String aName = a.getPath().substring(a.getPath().lastIndexOf('/') + 1);
                            String bName = b.getPath().substring(b.getPath().lastIndexOf('/') + 1);
                            boolean aHasPrefix = commonPrefixes.stream().anyMatch(prefix -> aName.startsWith(prefix));
                            boolean bHasPrefix = commonPrefixes.stream().anyMatch(prefix -> bName.startsWith(prefix));
                            if (aHasPrefix && !bHasPrefix) return -1;
                            if (!aHasPrefix && bHasPrefix) return 1;
                            return a.getPath().compareTo(b.getPath());
                        })
                        .collect(Collectors.toList())
        );

        // Update layer textures after reload
        LayerManager layerManager = LayerManager.getInstance();
        for (LayerInfo layer : layerManager.getAllLayers()) {
            // Remove any textures that no longer exist
            layer.getTextures().removeIf(texture -> !allBlockTextures.contains(texture));
        }
    }

    public static List<Identifier> getAllBlockTextures() {
        return new ArrayList<>(allBlockTextures);
    }

    public static List<Identifier> getUnassignedTextures() {
        Set<Identifier> assignedTextures = new HashSet<>();
        LayerManager.getInstance().getAllLayers().forEach(layer ->
                assignedTextures.addAll(layer.getTextures())
        );

        return allBlockTextures.stream()
                .filter(texture -> !assignedTextures.contains(texture))
                .collect(Collectors.toList());
    }

    public static List<Identifier> searchTextures(String query, LayerInfo layer) {
        List<Identifier> searchPool;
        if (layer != null) {
            // Search within the specified layer
            searchPool = new ArrayList<>(layer.getTextures());
        } else {
            // Search unassigned textures
            searchPool = getUnassignedTextures();
        }

        if (query == null || query.isEmpty()) {
            return searchPool.stream()
                    .limit(10)
                    .collect(Collectors.toList());
        }

        String lowerQuery = query.toLowerCase();
        return searchPool.stream()
                .filter(id -> {
                    String path = id.getPath();
                    String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                    return name.toLowerCase().contains(lowerQuery);
                })
                .limit(10)
                .collect(Collectors.toList());
    }

    public static void toggleTextureSelection(Identifier texture) {
        if (selectedTextures.contains(texture)) {
            selectedTextures.remove(texture);
        } else {
            selectedTextures.add(texture);
        }
    }

    public static void clearSelection() {
        selectedTextures.clear();
    }

    public static Set<Identifier> getSelectedTextures() {
        return new HashSet<>(selectedTextures);
    }

    public static boolean isSelected(Identifier texture) {
        return selectedTextures.contains(texture);
    }

    public static Identifier getDefaultTexture() {
        return Identifier.of("minecraft", "textures/block/grass_block_side.png");
    }

    public static void setCurrentPreviewTexture(Identifier texture) {
        currentPreviewTexture = texture;
        // Clear any existing selection when setting a new preview
        selectedTextures.clear();
        // Add the new preview texture to selection
        if (texture != null) {
            selectedTextures.add(texture);
        }
    }

    public static Identifier getCurrentPreviewTexture() {
        if (currentPreviewTexture == null) {
            currentPreviewTexture = getDefaultTexture();
        }
        return currentPreviewTexture;
    }

    public static void moveSelectedTexturesToLayer(LayerInfo targetLayer) {
        if (targetLayer == null || selectedTextures.isEmpty()) {
            return;
        }

        // Remove selected textures from all other layers
        LayerManager.getInstance().getAllLayers().forEach(layer -> {
            if (!layer.getId().equals(targetLayer.getId())) {
                selectedTextures.forEach(layer::removeTexture);
            }
        });

        // Add to target layer
        targetLayer.addTextures(selectedTextures);

        // Clear selection after move
        clearSelection();
    }
}