package em3.presets;

import com.fasterxml.jackson.databind.node.TextNode;
import em3.mindustry.MindustryV160;
import em3.project.Em3Project;
import em3.project.PartMoveModel;
import em3.project.RegionPartModel;
import em3.project.TurretContent;

/**
 * Salvo as defined by {@code Blocks.salvo} in Mindustry v160.7.
 * The drawer is a {@code DrawTurret} with two {@code RegionPart}s. There is no raw {@code salvo.png};
 * the in-world colored sprites are the barrel and the mirrored side plates. {@code salvo-preview}
 * is the icon and the shadow region.
 */
public final class SalvoPreset {
    private SalvoPreset() {
    }

    public static Em3Project create() {
        Em3Project project = new Em3Project();
        project.name = "Salvo";
        project.mindustryVersion = MindustryV160.VERSION_TAG;
        project.turret = turret();
        return project;
    }

    public static TurretContent turret() {
        TurretContent turret = new TurretContent();
        turret.type = "ItemTurret";
        turret.name = "salvo";
        turret.size = 2;
        turret.blockRecoil = 0f;
        turret.recoilPow = 1.8f;
        turret.elevation = 1f;
        turret.baseRegion = "block-2";
        turret.previewRegion = "salvo-preview";
        turret.bodyRegion = "salvo";

        RegionPartModel side = new RegionPartModel();
        side.id = "side";
        side.label = "Side";
        side.suffix = "-side";
        side.mirror = true;
        side.outline = true;
        side.progress = TextNode.valueOf("warmup");
        side.moveX = 0.6f;
        side.moveRot = -15f;
        side.layerOffset = 0.001f;
        side.heatProgress = TextNode.valueOf("heat");
        side.growProgress = TextNode.valueOf("warmup");
        PartMoveModel recoil = new PartMoveModel();
        recoil.progress = TextNode.valueOf("recoil");
        recoil.x = 0.5f;
        recoil.y = -0.5f;
        recoil.rot = -8f;
        side.moves.add(recoil);
        span(side, "warmup", "x", 0.6f);
        span(side, "warmup", "rotation", -15f);
        span(side, "recoil", "x", 0.5f);
        span(side, "recoil", "y", -0.5f);
        span(side, "recoil", "rotation", -8f);

        RegionPartModel barrel = new RegionPartModel();
        barrel.id = "barrel";
        barrel.label = "Barrel";
        barrel.suffix = "-barrel";
        barrel.mirror = false;
        barrel.outline = true;
        barrel.progress = TextNode.valueOf("recoil");
        barrel.moveY = -2.5f;
        barrel.heatProgress = TextNode.valueOf("heat");
        barrel.growProgress = TextNode.valueOf("warmup");
        span(barrel, "recoil", "y", -2.5f);

        turret.parts.add(side);
        turret.parts.add(barrel);
        return turret;
    }

    /** A linear 0 to 1 key pair matching a Mindustry move field. The original field stays. */
    private static void span(RegionPartModel part, String channel, String property, float value) {
        part.upsertKey(channel, property, 0f, 0f);
        part.upsertKey(channel, property, 1f, value);
    }
}
