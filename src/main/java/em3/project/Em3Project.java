package em3.project;

import em3.mindustry.MindustryV160;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native EM3 project. {@link #directory} is the project folder and is not written inside project.json.
 * Sprite values are paths relative to that folder when the project has been saved.
 */
public final class Em3Project {
    public int format = 1;
    public String name = "Salvo";
    public String mindustryVersion = MindustryV160.VERSION_TAG;
    public TurretContent turret = new TurretContent();
    /** Region name to project-relative or absolute file path. */
    public final Map<String, String> sprites = new LinkedHashMap<>();
    public transient Path directory;
    public transient boolean dirty = true;

    public Path resolveSprite(String region) {
        String ref = sprites.get(region);
        if (ref == null || ref.isBlank()) {
            return null;
        }
        Path path = Path.of(ref);
        if (path.isAbsolute() || directory == null) {
            return path;
        }
        return directory.resolve(path);
    }
}
