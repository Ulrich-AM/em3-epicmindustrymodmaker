package em3.app;

import em3.mindustry.MindustryV160;

/**
 * Editor-only ground-shadow preview. Null custom offsets keep DrawTurret's
 * {@code -elevation} on both world axes. Nothing here is exported.
 */
public final class ShadowDebug {
    public boolean visible = true;
    public boolean isolate;
    public float opacity = MindustryV160.SHADOW_ALPHA;
    public Float customOffsetX;
    public Float customOffsetY;

    public float offsetX(float elevation) {
        return customOffsetX == null ? -elevation : customOffsetX;
    }

    public float offsetY(float elevation) {
        return customOffsetY == null ? -elevation : customOffsetY;
    }
}
