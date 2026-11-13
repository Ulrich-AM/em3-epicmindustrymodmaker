package em3.app;

import em3.mindustry.MindustryV160;

/** Editor-only viewport toggles. None of these are written into exported part properties. */
public final class Overlays {
    public boolean outlines = true;
    public boolean names;
    public boolean pivots;
    public boolean bounds;
    public boolean symmetry;
    public boolean heat = true;
    public boolean transparency;
    public boolean layerOrder;
    public boolean paths;
    public boolean base = true;
    public boolean grid = true;
    public int outlineRadius = MindustryV160.OUTLINE_RADIUS;
    public int outlineArgb = MindustryV160.OUTLINE_ARGB;
}
