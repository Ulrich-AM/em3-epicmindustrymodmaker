package em3.assets;

import em3.mindustry.MindustryV160;
import em3.project.Em3Project;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the six official v160.7 Salvo PNGs.
 * Mindustry is GPL-3.0. These files are downloaded or copied beside the app.
 * They are not stored in the EM3 git tree.
 */
public final class SalvoAssets {
    private SalvoAssets() {
    }

    public static Path cacheDir() {
        return SpriteLibrary.userCache();
    }

    /** Directory next to a packaged EM3.exe, or next to the classes while developing. */
    public static Path bundledDir() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(System.getProperty("user.dir"), "sprites", "salvo"));
        try {
            var source = SalvoAssets.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path code = Path.of(source.getLocation().toURI());
                Path parent = code.getParent();
                if (parent != null) {
                    candidates.add(parent.resolve("sprites").resolve("salvo"));
                    if (parent.getParent() != null) {
                        candidates.add(parent.getParent().resolve("sprites").resolve("salvo"));
                    }
                }
            }
        } catch (Exception ignored) {
            // A missing code source just skips the bundled lookup.
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("salvo-barrel.png"))) {
                return candidate;
            }
        }
        return null;
    }

    /** Links official sprites when the project does not already name them. Existing paths are kept. */
    public static void ensure(Em3Project project) {
        if (project == null) {
            return;
        }
        boolean missing = false;
        for (String region : MindustryV160.SALVO_REGIONS) {
            if (!project.sprites.containsKey(region)) {
                missing = true;
                break;
            }
        }
        if (!missing) {
            return;
        }
        Path bundled = bundledDir();
        Path cache = cacheDir();
        for (String region : MindustryV160.SALVO_REGIONS) {
            if (project.sprites.containsKey(region)) {
                continue;
            }
            Path file = firstExisting(region, bundled, cache);
            if (file != null) {
                project.sprites.put(region, file.toAbsolutePath().toString());
                project.dirty = true;
            }
        }
    }

    public static List<String> downloadToCache() throws IOException {
        Path cache = cacheDir();
        Files.createDirectories(cache);
        List<String> written = new ArrayList<>();
        for (String region : MindustryV160.SALVO_REGIONS) {
            Path dest = cache.resolve(region + ".png");
            if (!Files.isRegularFile(dest)) {
                URI uri = URI.create(MindustryV160.rawSpriteUrl(region));
                try (InputStream in = uri.toURL().openStream()) {
                    Files.copy(in, dest);
                }
            }
            written.add(region);
        }
        return written;
    }

    private static Path firstExisting(String region, Path... dirs) {
        for (Path dir : dirs) {
            if (dir == null) {
                continue;
            }
            Path file = dir.resolve(region + ".png");
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }
}
