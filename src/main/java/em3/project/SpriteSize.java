package em3.project;

/** Pixel size of a region. {@code found} is false when the file is absent. */
public record SpriteSize(int width, int height, boolean found, boolean generated, String name) {
    public static SpriteSize missing() {
        return new SpriteSize(0, 0, false, false, "");
    }

    public static SpriteSize of(int width, int height, String name) {
        return new SpriteSize(width, height, true, false, name);
    }

    public static SpriteSize generated(int width, int height, String sourceName) {
        return new SpriteSize(width, height, false, true, sourceName);
    }
}
