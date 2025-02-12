package ninja.trek;

import com.google.gson.annotations.Expose;
import net.minecraft.util.Identifier;
import java.util.*;

public class LayerInfo {
    @Expose private final UUID id;
    @Expose private String name;
    @Expose private Set<Identifier> textures;
    @Expose private int contrast;
    @Expose private int saturation;
    @Expose private int hue;  // New field for hue adjustment
    @Expose private String palette;

    public LayerInfo(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.textures = new HashSet<>();
        this.contrast = 0;
        this.saturation = 0;
        this.hue = 0;  // Initialize hue to 0 (no adjustment)
        this.palette = "builtin_1";
    }

    // Existing getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Set<Identifier> getTextures() { return new HashSet<>(textures); }
    public int getContrast() { return contrast; }
    public int getSaturation() { return saturation; }
    public int getHue() { return hue; }  // New getter for hue
    public String getPalette() { return palette; }

    // Existing setters
    public void setName(String name) { this.name = name; }
    public void setContrast(int contrast) {
        this.contrast = Math.min(Math.max(contrast, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
    }
    public void setSaturation(int saturation) {
        this.saturation = Math.min(Math.max(saturation, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
    }
    public void setHue(int hue) {  // New setter for hue
        this.hue = Math.min(Math.max(hue, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
    }
    public void setPalette(String palette) { this.palette = palette; }

    // Texture management methods
    public void addTexture(Identifier texture) { textures.add(texture); }
    public void removeTexture(Identifier texture) { textures.remove(texture); }
    public void addTextures(Collection<Identifier> textures) { this.textures.addAll(textures); }
    public void clearTextures() { textures.clear(); }
}