package em3.animation;

/**
 * Editor playback curve that fills {@link PartParams}.
 * This is not {@code TurretBuild.update}. It only produces the 0-1 channels the part evaluator reads,
 * shaped so a Salvo-style shot is recognizable: recoil and heat start high and fall, reload goes from
 * just-fired (1) toward ready (0), and warmup rises while the shot is active.
 */
public final class PreviewDriver {
    private PreviewDriver() {
    }

    public static void apply(float cycle, PartParams params) {
        float t = cycle >= 1f ? 1f : cycle - (float) Math.floor(cycle);
        if (t < 0f) {
            t += 1f;
        }
        params.recoil = (float) Math.pow(Math.max(0f, 1f - t / 0.45f), 1.8);
        params.reload = 1f - t;
        params.smoothReload = params.reload;
        params.heat = (float) Math.pow(Math.max(0f, 1f - t / 0.70f), 1.0);
        if (t < 0.85f) {
            params.warmup = Math.min(1f, t / 0.18f);
        } else {
            params.warmup = Math.max(0f, 1f - (t - 0.85f) / 0.15f);
        }
        params.charge = t < 0.08f ? t / 0.08f : 0f;
        params.life = 0f;
    }
}
