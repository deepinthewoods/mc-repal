package ninja.trek;

import java.nio.file.Path;

public class PaletteInfo {
    private final String name;
    private final Path path;
    private final boolean isBuiltin;

    public PaletteInfo(String name, Path path, boolean isBuiltin) {
        this.name = name;
        this.path = path;
        this.isBuiltin = isBuiltin;
    }

    public String getName() { return name; }
    public Path getPath() { return path; }
    public boolean isBuiltin() { return isBuiltin; }
}