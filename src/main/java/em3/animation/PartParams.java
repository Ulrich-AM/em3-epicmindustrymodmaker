package em3.animation;

/**
 * Editor copy of {@code mindustry.entities.part.DrawPart.PartParams}.
 * Channel ranges are the values Mindustry stores on the struct, not the turret reload counter.
 * {@code DrawTurret} passes {@code 1 - TurretBuild.progress()} as {@code reload} and {@code smoothReload},
 * so 1 means just fired and 0 means ready.
 */
public final class PartParams {
    public float warmup;
    public float reload;
    public float smoothReload;
    public float heat;
    public float recoil;
    public float charge;
    public float life;
    public float x;
    public float y;
    /** Mindustry turret rotation in degrees. 0 faces east. Sprite draw rotation is this minus 90. */
    public float rotation = 90f;
    public int sideOverride = -1;
    public int sideMultiplier = 1;

    public PartParams copy() {
        PartParams c = new PartParams();
        c.warmup = warmup;
        c.reload = reload;
        c.smoothReload = smoothReload;
        c.heat = heat;
        c.recoil = recoil;
        c.charge = charge;
        c.life = life;
        c.x = x;
        c.y = y;
        c.rotation = rotation;
        c.sideOverride = sideOverride;
        c.sideMultiplier = sideMultiplier;
        return c;
    }

    public float channel(String name) {
        return switch (name) {
            case "warmup" -> warmup;
            case "reload" -> reload;
            case "smoothReload" -> smoothReload;
            case "heat" -> heat;
            case "recoil" -> recoil;
            case "charge" -> charge;
            case "life" -> life;
            default -> Float.NaN;
        };
    }
}
