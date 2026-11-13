package em3.animation;

import java.util.ArrayList;
import java.util.List;

/** Keyframes for one property on one progress channel. */
public final class AnimTrack {
    /** x, y, rotation, xScl, or yScl. Position and rotation values are offsets added to the part base. Scale values are added to the part scale. */
    public String property = "x";
    public final List<Keyframe> keys = new ArrayList<>();

    public AnimTrack() {
    }

    public AnimTrack(String property) {
        this.property = property;
    }

    public AnimTrack copy() {
        AnimTrack c = new AnimTrack(property);
        for (Keyframe key : keys) {
            c.keys.add(key.copy());
        }
        return c;
    }
}
