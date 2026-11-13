package em3.assets;

import java.awt.image.BufferedImage;

/**
 * Port of Arc {@code Pixmaps.outline(PixmapRegion, Color, int)}.
 * A pixel whose alpha is below 255 becomes the outline color when any pixel inside the radius
 * has a non-zero alpha. The radius test is {@code rx*rx + ry*ry <= radius*radius}, not a 1-pixel border.
 * <p>
 * Mindustry then calls {@code Drawf.checkBleed}, which runs {@code Pixmaps.bleed} only when the game's
 * linear filter setting is on. EM3 samples sprites with nearest-neighbor filtering, so bleed is not applied.
 */
public final class OutlineImages {
    private OutlineImages() {
    }

    public static BufferedImage outline(BufferedImage source, int argb, int radius) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);
        int[] out = pixels.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (pixels[y * w + x] >>> 24) & 0xff;
                if (alpha < 255) {
                    boolean found = false;
                    outer:
                    for (int rx = -radius; rx <= radius; rx++) {
                        for (int ry = -radius; ry <= radius; ry++) {
                            int nx = x + rx;
                            int ny = y + ry;
                            if (nx >= 0 && ny >= 0 && nx < w && ny < h
                                    && (rx * rx + ry * ry <= radius * radius)
                                    && ((pixels[ny * w + nx] >>> 24) & 0xff) != 0) {
                                found = true;
                                break outer;
                            }
                        }
                    }
                    if (found) {
                        out[y * w + x] = argb;
                    }
                }
            }
        }
        copy.setRGB(0, 0, w, h, out, 0, w);
        return copy;
    }
}
