package em3.animation;

/**
 * Corner placement copied from Arc {@code SpriteBatch.constructVertices}.
 * Rotation uses {@link Math#cos} / {@link Math#sin}. Arc's {@code Mathf.cosDeg} is a lookup table,
 * so a corner can differ by a fraction of a pixel. Position offsets in {@link RegionMath} use
 * {@code Vec2.rotateRadExact}, which is already {@code Math.cos}.
 */
public final class Quad {
    private Quad() {
    }

    public static void place(float[] out, float x, float y, float originX, float originY,
                              float width, float height, float rotationDeg) {
        if (Math.abs(rotationDeg) > 0.0001f) {
            float worldOriginX = x + originX;
            float worldOriginY = y + originY;
            float fx = -originX;
            float fy = -originY;
            float fx2 = width - originX;
            float fy2 = height - originY;
            double rad = Math.toRadians(rotationDeg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float x1 = cos * fx - sin * fy + worldOriginX;
            float y1 = sin * fx + cos * fy + worldOriginY;
            float x2 = cos * fx - sin * fy2 + worldOriginX;
            float y2 = sin * fx + cos * fy2 + worldOriginY;
            float x3 = cos * fx2 - sin * fy2 + worldOriginX;
            float y3 = sin * fx2 + cos * fy2 + worldOriginY;
            float x4 = x1 + (x3 - x2);
            float y4 = y3 - (y2 - y1);
            out[0] = x1;
            out[1] = y1;
            out[2] = x2;
            out[3] = y2;
            out[4] = x3;
            out[5] = y3;
            out[6] = x4;
            out[7] = y4;
        } else {
            out[0] = x;
            out[1] = y;
            out[2] = x;
            out[3] = y + height;
            out[4] = x + width;
            out[5] = y + height;
            out[6] = x + width;
            out[7] = y;
        }
    }
}
