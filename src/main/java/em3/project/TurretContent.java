package em3.project;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** Turret content kept apart from any Mindustry runtime object. */
public final class TurretContent {
    public String type = "ItemTurret";
    public String name = "salvo";
    public int size = 2;
    /** {@code Turret.recoil}. Salvo sets this to 0, so the whole body does not kick back. */
    public float blockRecoil = 0f;
    public float recoilPow = 1.8f;
    /** Negative means {@code size / 2}, matching {@code Turret} init. */
    public float elevation = 1f;
    public String baseRegion = "block-2";
    public String previewRegion = "salvo-preview";
    /** {@code block.region}. Salvo has no raw {@code salvo.png}; the atlas region is absent. */
    public String bodyRegion = "salvo";
    public final List<RegionPartModel> parts = new ArrayList<>();
    public ObjectNode extras;

    public TurretContent copy() {
        TurretContent c = new TurretContent();
        c.type = type;
        c.name = name;
        c.size = size;
        c.blockRecoil = blockRecoil;
        c.recoilPow = recoilPow;
        c.elevation = elevation;
        c.baseRegion = baseRegion;
        c.previewRegion = previewRegion;
        c.bodyRegion = bodyRegion;
        for (RegionPartModel part : parts) {
            c.parts.add(part.copy());
        }
        c.extras = extras == null ? null : extras.deepCopy();
        return c;
    }

    public RegionPartModel find(String id) {
        for (RegionPartModel part : parts) {
            RegionPartModel found = find(part, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private RegionPartModel find(RegionPartModel part, String id) {
        if (part.id.equals(id)) {
            return part;
        }
        for (RegionPartModel child : part.children) {
            RegionPartModel found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
