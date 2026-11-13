package em3.mindustry;

/**
 * Constants taken from Mindustry v160.7 and Arc, pinned so the preview does not drift.
 * <p>
 * Sprite scale is {@code tilesize / 32}: a tile is 8 world units and block art is authored at 32 pixels per tile.
 * Outline radius and color are {@code Block.outlineRadius} and {@code Block.outlineColor}.
 * Part heat tint is {@code Pal.turretHeat}. Shadow tint is {@code Pal.shadow}.
 * Layer numbers are {@code mindustry.graphics.Layer}.
 */
public final class MindustryV160 {
    public static final String VERSION_TAG = "v160.7";
    public static final String VERSION_LABEL = "v8 build 160.7";

    public static final float TILE_SIZE = 8f;
    public static final float PIXELS_PER_TILE = 32f;
    /** {@code TextureRegion.scl()} for world sprites. World size = pixels * SPRITE_SCALE. */
    public static final float SPRITE_SCALE = TILE_SIZE / PIXELS_PER_TILE;

    public static final int OUTLINE_RADIUS = 4;
    /** {@code Color.valueOf("404049")} at full alpha, stored as Java ARGB. */
    public static final int OUTLINE_ARGB = 0xFF404049;

    public static final int TURRET_HEAT_ARGB = 0xFFAB3400;
    public static final float SHADOW_ALPHA = 0.22f;

    public static final float LAYER_BLOCK = 30f;
    public static final float LAYER_TURRET = 50f;
    public static final float LAYER_TURRET_HEAT = 50.1f;
    public static final float LAYER_SHADOW = LAYER_TURRET - 0.5f;
    public static final float TURRET_OUTLINE_Z = LAYER_TURRET - 0.01f;

    public static final float DEFAULT_OUTLINE_LAYER_OFFSET = -0.001f;
    public static final float DEFAULT_HEAT_LAYER_OFFSET = 1f;

    /** Raw files used to assemble Salvo. Git does not store the PNGs. The Windows package may. */
    public static final String[] SALVO_REGIONS = {
            "salvo-preview",
            "salvo-barrel",
            "salvo-barrel-heat",
            "salvo-side-l",
            "salvo-side-r",
            "block-2"
    };

    public static final String RAW_BASE =
            "https://raw.githubusercontent.com/Anuken/Mindustry/" + VERSION_TAG + "/";

    private MindustryV160() {
    }

    public static String rawSpriteUrl(String region) {
        if ("block-2".equals(region)) {
            return RAW_BASE + "core/assets-raw/sprites/blocks/turrets/bases/block-2.png";
        }
        return RAW_BASE + "core/assets-raw/sprites/blocks/turrets/salvo/" + region + ".png";
    }

    public static float elevationFor(int size, float elevation) {
        return elevation < 0f ? size / 2f : elevation;
    }
}
