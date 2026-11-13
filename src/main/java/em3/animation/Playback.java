package em3.animation;

/** Scrubber and transport for the animation preview. Not a keyframe timeline. */
public final class Playback {
    public float cycle;
    public boolean playing;
    public boolean loop = true;
    public boolean reverse;
    public boolean spinTurret;
    /** When true, Play and the scrubber write PartParams. Editing a channel turns this off. */
    public boolean driveChannels = true;
    /** Cycles per second at speed 1. */
    public float cyclesPerSecond = 0.35f;
    public float speed = 1f;
    public boolean rotateWithPlayback;

    public void reset(PartParams params) {
        cycle = 0f;
        playing = false;
        driveChannels = true;
        params.warmup = 0f;
        params.reload = 0f;
        params.smoothReload = 0f;
        params.heat = 0f;
        params.recoil = 0f;
        params.charge = 0f;
        params.life = 0f;
    }

    public void advance(float dtSeconds, PartParams params) {
        if (!playing) {
            return;
        }
        float dir = reverse ? -1f : 1f;
        cycle += dir * dtSeconds * cyclesPerSecond * Math.max(0.01f, speed);
        if (loop) {
            cycle = cycle - (float) Math.floor(cycle);
            if (cycle < 0f) {
                cycle += 1f;
            }
        } else if (cycle >= 1f || cycle <= 0f) {
            cycle = Math.max(0f, Math.min(1f, cycle));
            playing = false;
        }
        if (driveChannels) {
            PreviewDriver.apply(cycle, params);
        }
        if (rotateWithPlayback) {
            params.rotation = (params.rotation + dir * dtSeconds * 40f) % 360f;
            if (params.rotation < 0f) {
                params.rotation += 360f;
            }
        }
    }

    public void scrub(float cycle, PartParams params) {
        this.cycle = Math.max(0f, Math.min(1f, cycle));
        driveChannels = true;
        PreviewDriver.apply(this.cycle, params);
    }
}
