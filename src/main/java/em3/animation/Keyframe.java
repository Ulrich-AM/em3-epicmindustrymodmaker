package em3.animation;

/** One key on a normalized 0–1 track. */
public final class Keyframe {
    public float time;
    public float value;
    /** {@code linear} or {@code step}. Step is preview-only. */
    public String interpolation = "linear";

    public Keyframe() {
    }

    public Keyframe(float time, float value) {
        this.time = time;
        this.value = value;
    }

    public Keyframe copy() {
        Keyframe c = new Keyframe(time, value);
        c.interpolation = interpolation;
        return c;
    }
}
