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
    @Expose private String palette;

    public LayerInfo(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.textures = new HashSet<>();
        this.contrast = 0;
        this.saturation = 0;
        this.palette = "builtin_1";
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Set<Identifier> getTextures() { return new HashSet<>(textures); }
    public int getContrast() { return contrast; }
    public int getSaturation() { return saturation; }
    public String getPalette() { return palette; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setContrast(int contrast) {
        this.contrast = Math.min(Math.max(contrast, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
    }
    public void setSaturation(int saturation) {
        this.saturation = Math.min(Math.max(saturation, Repal.MIN_ADJUSTMENT), Repal.MAX_ADJUSTMENT);
    }
    public void setPalette(String palette) { this.palette = palette; }

    // Texture management
    public void addTexture(Identifier texture) { textures.add(texture); }
    public void removeTexture(Identifier texture) { textures.remove(texture); }
    public void addTextures(Collection<Identifier> textures) { this.textures.addAll(textures); }
    public void clearTextures() { textures.clear(); }
}

