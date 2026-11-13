package em3.animation;

/**
 * Editor aim-and-fire preview. It writes {@link PartParams} channels.
 * It is not {@code TurretBuild.update}.
 */
public final class TestPreview {
    public boolean active;
    public boolean frozen;
    public boolean paused;
    public boolean autoWarmup = true;
    public boolean autoCooldown = true;
    public boolean repeat = true;
    public boolean loop = true;
    public float fireRate = 2f;
    public float rotationSpeed = 180f;
    public float timeScale = 1f;
    public float aimDegrees = 90f;
    public boolean triggerHeld;
    public String phase = "idle";
    public float untilNextShot;
    public int shots;

    public void reset(PartParams params) {
        phase = "idle";
        untilNextShot = 0f;
        shots = 0;
        frozen = false;
        paused = false;
        params.warmup = 0f;
        params.reload = 0f;
        params.smoothReload = 0f;
        params.heat = 0f;
        params.recoil = 0f;
        params.charge = 0f;
    }

    public void step(float dtSeconds, PartParams params) {
        if (!active || frozen || paused) {
            return;
        }
        float dt = Math.max(0f, dtSeconds) * Math.max(0f, timeScale);
        float delta = wrap(aimDegrees - params.rotation);
        float maxStep = rotationSpeed * dt;
        params.rotation += Math.copySign(Math.min(Math.abs(delta), maxStep), delta);

        boolean shooting = triggerHeld && repeat && (loop || shots == 0);
        if (shooting && autoWarmup) {
            params.warmup = Math.min(1f, params.warmup + dt / 0.25f);
            phase = params.warmup < 0.98f ? "warming" : "ready";
            untilNextShot -= dt;
            if (params.warmup >= 0.8f && untilNextShot <= 0f) {
                fire(params);
            }
        } else if (autoCooldown) {
            params.warmup = Math.max(0f, params.warmup - dt / 0.45f);
            params.heat = Math.max(0f, params.heat - dt / 0.7f);
            phase = params.warmup > 0.02f || params.heat > 0.02f ? "cooling" : "idle";
        }
        params.recoil = Math.max(0f, params.recoil - dt / 0.28f);
        params.reload = Math.max(0f, params.reload - dt * Math.max(0.5f, fireRate));
        params.smoothReload = params.reload;
        params.charge = Math.max(0f, params.charge - dt / 0.12f);
        if (params.recoil > 0.15f) {
            phase = "recoil";
        }
    }

    public void fire(PartParams params) {
        phase = "firing";
        params.recoil = 1f;
        params.reload = 1f;
        params.smoothReload = 1f;
        params.heat = Math.min(1f, params.heat + 0.4f);
        params.charge = 1f;
        untilNextShot = 1f / clampRate(fireRate);
        shots++;
    }

    public static float clampRate(float rate) {
        return Math.max(0.5f, Math.min(15f, rate));
    }

    public static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}
