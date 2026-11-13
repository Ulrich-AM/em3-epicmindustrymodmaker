package em3.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Samples keyframe tracks. Values before the first key are 0 so a later key eases in from rest. */
public final class TrackMath {
    private TrackMath() {
    }

    public static float sample(AnimTrack track, float time) {
        if (track == null || track.keys.isEmpty()) {
            return 0f;
        }
        List<Keyframe> keys = new ArrayList<>(track.keys);
        keys.sort(Comparator.comparingDouble(k -> k.time));
        if (time < keys.get(0).time) {
            return 0f;
        }
        if (time == keys.get(0).time) {
            return keys.get(0).value;
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            Keyframe a = keys.get(i);
            Keyframe b = keys.get(i + 1);
            if (time <= b.time) {
                if ("step".equals(segmentMode(a, b)) && time < b.time) {
                    return a.value;
                }
                float span = b.time - a.time;
                float u = span <= 1e-6f ? 1f : (time - a.time) / span;
                return a.value + (b.value - a.value) * u;
            }
        }
        return keys.get(keys.size() - 1).value;
    }

    /** The interpolation that applies while traveling from {@code a} toward {@code b}. */
    public static String segmentMode(Keyframe a, Keyframe b) {
        if (b != null && "step".equals(b.interpolation)) {
            return "step";
        }
        return "linear";
    }

    public static Keyframe upsert(AnimTrack track, float time, float value) {
        float t = clamp01(time);
        for (Keyframe key : track.keys) {
            if (Math.abs(key.time - t) < 0.001f) {
                key.value = value;
                return key;
            }
        }
        Keyframe created = new Keyframe(t, value);
        track.keys.add(created);
        return created;
    }

    public static float clamp01(float time) {
        return Math.max(0f, Math.min(1f, time));
    }

    public static final class Segment {
        public final float start;
        public final float end;
        public final float delta;
        public final boolean step;

        public Segment(float start, float end, float delta, boolean step) {
            this.start = start;
            this.end = end;
            this.delta = delta;
            this.step = step;
        }
    }

    public static List<Segment> segments(AnimTrack track) {
        List<Segment> out = new ArrayList<>();
        if (track == null || track.keys.size() < 2) {
            return out;
        }
        List<Keyframe> keys = new ArrayList<>(track.keys);
        keys.sort(Comparator.comparingDouble(k -> k.time));
        for (int i = 0; i < keys.size() - 1; i++) {
            Keyframe a = keys.get(i);
            Keyframe b = keys.get(i + 1);
            boolean step = "step".equals(segmentMode(a, b));
            out.add(new Segment(a.time, b.time, b.value - a.value, step));
        }
        return out;
    }
}
