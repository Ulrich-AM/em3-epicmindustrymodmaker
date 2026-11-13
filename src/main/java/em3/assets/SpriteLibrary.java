package em3.assets;

import em3.mindustry.MindustryV160;
import em3.project.Em3Project;
import em3.project.SpriteSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads PNG regions for the open project and can copy official v160.7 raw sprites into a cache. */
public final class SpriteLibrary {
    private final Map<String, BufferedImage> images = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();

    public void reload(Em3Project project) {
        images.clear();
        failures.clear();
        for (var entry : project.sprites.entrySet()) {
            Path path = project.resolveSprite(entry.getKey());
            if (path == null || !Files.isRegularFile(path)) {
                failures.put(entry.getKey(), path == null ? "no path" : "missing file " + path);
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image == null) {
                    failures.put(entry.getKey(), "unreadable PNG " + path);
                } else {
                    images.put(entry.getKey(), image);
                }
            } catch (IOException e) {
                failures.put(entry.getKey(), e.getMessage());
            }
        }
    }

    public BufferedImage image(String region) {
        return images.get(region);
    }

    public SpriteSize size(String region) {
        BufferedImage image = images.get(region);
        if (image == null) {
            return SpriteSize.missing();
        }
        return SpriteSize.of(image.getWidth(), image.getHeight(), region);
    }

    public Map<String, String> failures() {
        return failures;
    }

    public boolean has(String region) {
        return images.containsKey(region);
    }

    public static Map<String, Path> scan(Path root) throws IOException {
        Map<String, Path> found = new LinkedHashMap<>();
        if (root == null || !Files.exists(root)) {
            return found;
        }
        Path start = Files.isDirectory(root) ? root : root.getParent();
        if (start == null || !Files.isDirectory(start)) {
            return found;
        }
        try (var walk = Files.walk(start)) {
            walk.filter(path -> path.getFileName().toString().endsWith(".png")).forEach(path -> {
                String region = path.getFileName().toString().replaceFirst("\\.png$", "");
                Path previous = found.get(region);
                if (previous == null || score(path) > score(previous)) {
                    found.put(region, path);
                }
            });
        }
        return found;
    }

    private static int score(Path path) {
        String text = path.toString().replace('\\', '/');
        int score = 0;
        if (text.contains("/turrets/salvo/")) {
            score += 5;
        }
        if (text.contains("/turrets/bases/")) {
            score += 4;
        }
        if (text.contains("/assets-raw/")) {
            score += 1;
        }
        return score;
    }

    public static List<String> importRegions(Em3Project project, Map<String, Path> found, String... regions) throws IOException {
        List<String> missing = new ArrayList<>();
        for (String region : regions) {
            Path path = found.get(region);
            if (path == null || !Files.isRegularFile(path)) {
                missing.add(region);
                continue;
            }
            project.sprites.put(region, path.toAbsolutePath().toString());
        }
        project.dirty = true;
        return missing;
    }

    public static Path userCache() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".em3", "cache", "mindustry-" + MindustryV160.VERSION_TAG);
    }

    public static List<String> downloadOfficial(Path cache) throws IOException {
        Files.createDirectories(cache);
        List<String> written = new ArrayList<>();
        for (String region : MindustryV160.SALVO_REGIONS) {
            Path dest = cache.resolve(region + ".png");
            URI uri = URI.create(MindustryV160.rawSpriteUrl(region));
            try (InputStream in = uri.toURL().openStream()) {
                Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            written.add(region);
        }
        return written;
    }
}
