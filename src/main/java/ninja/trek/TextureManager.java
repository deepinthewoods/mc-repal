package ninja.trek;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextureManager {
    private static final Pattern BLOCK_TEXTURE_PATTERN = Pattern.compile("textures/block/.*\\.png");
    private static List<Identifier> blockTextures = new ArrayList<>();
    private static Identifier currentPreviewTexture = null;
    private static final Set<String> commonPrefixes = new HashSet<>(Arrays.asList(
            "stone", "dirt", "grass", "planks", "log", "leaves",
            "ore", "wool", "glass", "concrete", "terracotta"
    ));

    public static void loadTextures(ResourceManager resourceManager) {
        blockTextures = resourceManager.findResources("textures/block", id ->
                        BLOCK_TEXTURE_PATTERN.matcher(id.getPath()).matches()
                ).keySet().stream()
                .sorted((a, b) -> {
                    // Sort by common prefixes first
                    String aName = a.getPath().substring(a.getPath().lastIndexOf('/') + 1);
                    String bName = b.getPath().substring(b.getPath().lastIndexOf('/') + 1);

                    boolean aHasPrefix = commonPrefixes.stream().anyMatch(prefix -> aName.startsWith(prefix));
                    boolean bHasPrefix = commonPrefixes.stream().anyMatch(prefix -> bName.startsWith(prefix));

                    if (aHasPrefix && !bHasPrefix) return -1;
                    if (!aHasPrefix && bHasPrefix) return 1;

                    return a.getPath().compareTo(b.getPath());
                })
                .collect(Collectors.toList());
    }

    public static List<Identifier> getBlockTextures() {
        return blockTextures;
    }

    public static List<Identifier> searchTextures(String query) {
        if (query == null || query.isEmpty()) {
            return blockTextures.stream()
                    .limit(10)
                    .collect(Collectors.toList());
        }

        String lowerQuery = query.toLowerCase();
        return blockTextures.stream()
                .filter(id -> {
                    String path = id.getPath();
                    String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                    return name.toLowerCase().contains(lowerQuery);
                })
                .limit(10)
                .collect(Collectors.toList());
    }

    public static Identifier getDefaultTexture() {
        return Identifier.of("minecraft", "textures/block/grass_block_side.png");
    }

    public static void setCurrentPreviewTexture(Identifier texture) {
        currentPreviewTexture = texture;
    }

    public static Identifier getCurrentPreviewTexture() {
        if (currentPreviewTexture == null) {
            currentPreviewTexture = getDefaultTexture();
        }
        return currentPreviewTexture;
    }
}