package em3.animation;

/** One textured quad in world space, already evaluated. */
public final class SpritePlacement {
    public String partId = "";
    public String selectionKey = "";
    public String label = "";
    public String sprite = "";
    public int side = -1;
    /** base, shadow, outline, region, heat */
    public String role = "region";
    public float z;
    public int order;
    public float centerX;
    public float centerY;
    public float rotation;
    public float width;
    public float height;
    public float originX;
    public float originY;
    public float r = 1f;
    public float g = 1f;
    public float b = 1f;
    public float a = 1f;
    public boolean additive;
    public boolean missing;
    /** World-space corners: x0,y0 ... x3,y3 in the same order Arc's SpriteBatch emits. */
    public final float[] corners = new float[8];

    public void buildCorners() {
        float x = centerX - width / 2f;
        float y = centerY - height / 2f;
        Quad.place(corners, x, y, originX, originY, width, height, rotation);
    }

    public boolean contains(float wx, float wy) {
        // Even-odd test on the quad.
        boolean inside = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            float xi = corners[i * 2];
            float yi = corners[i * 2 + 1];
            float xj = corners[j * 2];
            float yj = corners[j * 2 + 1];
            if (((yi > wy) != (yj > wy)) && (wx < (xj - xi) * (wy - yi) / ((yj - yi) == 0f ? 1e-6f : (yj - yi)) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
