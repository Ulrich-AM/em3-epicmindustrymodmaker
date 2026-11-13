package em3;

import com.fasterxml.jackson.databind.node.TextNode;
import em3.animation.Keyframe;
import em3.animation.PartParams;
import em3.animation.Playback;
import em3.animation.RegionMath;
import em3.animation.SpritePlacement;
import em3.animation.TestPreview;
import em3.animation.TrackMath;
import em3.app.History;
import em3.app.ShadowDebug;
import em3.assets.SalvoAssets;
import em3.export.ContentExporter;
import em3.export.ExportReport;
import em3.mindustry.MindustryV160;
import em3.presets.SalvoPreset;
import em3.project.Em3Project;
import em3.project.ProjectStore;
import em3.project.RegionPartModel;
import em3.project.SpriteSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationEditorTest {
    private static final Function<String, SpriteSize> MISSING = name -> SpriteSize.missing();

    @Test
    void linearKeysInterpolateAndHold() {
        var track = new em3.animation.AnimTrack("x");
        track.keys.add(new Keyframe(0f, 0f));
        track.keys.add(new Keyframe(1f, 10f));
        assertEquals(0f, TrackMath.sample(track, 0f), 0.001f);
        assertEquals(5f, TrackMath.sample(track, 0.5f), 0.001f);
        assertEquals(10f, TrackMath.sample(track, 1f), 0.001f);
        assertEquals(0f, TrackMath.sample(track, -1f), 0.001f);
        assertEquals(10f, TrackMath.sample(track, 2f), 0.001f);
    }

    @Test
    void stepKeysHoldUntilTheNextTime() {
        var track = new em3.animation.AnimTrack("x");
        Keyframe end = new Keyframe(1f, 4f);
        end.interpolation = "step";
        track.keys.add(new Keyframe(0f, 1f));
        track.keys.add(end);
        assertEquals(1f, TrackMath.sample(track, 0.9f), 0.001f);
        assertEquals(4f, TrackMath.sample(track, 1f), 0.001f);
    }

    @Test
    void upsertInsertsAndUpdates() {
        var part = new RegionPartModel();
        part.upsertKey("warmup", "x", 0.5f, 2f);
        part.upsertKey("warmup", "x", 0.5f, 3f);
        assertEquals(1, part.channel("warmup").track("x").keys.size());
        assertEquals(3f, part.sampleTrack("warmup", "x", 0.5f), 0.001f);
        part.upsertKey("warmup", "x", 0.8f, 4f);
        assertEquals(2, part.channel("warmup").track("x").keys.size());
        part.channel("warmup").track("x").keys.remove(0);
        assertEquals(1, part.channel("warmup").track("x").keys.size());
    }

    @Test
    void scrubMovesThePreviewChannels() {
        Playback playback = new Playback();
        PartParams params = new PartParams();
        playback.scrub(0.5f, params);
        assertEquals(0.5f, playback.cycle, 0.001f);
        assertTrue(params.recoil >= 0f && params.recoil <= 1f);
        assertFalse(playback.playing);
    }

    @Test
    void projectRoundTripKeepsTracksAndOldProjectsOpen(@TempDir Path dir) throws Exception {
        Em3Project project = SalvoPreset.create();
        project.turret.find("barrel").upsertKey("recoil", "y", 0.25f, -1f);
        ProjectStore.save(project, dir);
        Em3Project opened = ProjectStore.open(dir);
        assertEquals(-1f, opened.turret.find("barrel").sampleTrack("recoil", "y", 0.25f), 0.001f);
        assertFalse(opened.turret.find("side").moves.isEmpty());

        String legacy = """
                {
                  "format": 1,
                  "name": "old",
                  "mindustryVersion": "v160.7",
                  "turret": {
                    "type": "ItemTurret",
                    "name": "salvo",
                    "size": 2,
                    "parts": [ { "type": "RegionPart", "suffix": "-barrel", "id": "barrel", "label": "Barrel", "moveY": -2.5 } ]
                  },
                  "sprites": {}
                }
                """;
        Path old = dir.resolve("legacy");
        Files.createDirectories(old);
        Files.writeString(old.resolve("project.json"), legacy);
        Em3Project legacyProject = ProjectStore.open(old);
        assertEquals("barrel", legacyProject.turret.find("barrel").id);
        assertTrue(legacyProject.turret.find("barrel").animations.isEmpty());
        assertEquals(-2.5f, legacyProject.turret.find("barrel").moveY);
    }

    @Test
    void rectangleExportsAsCompressMoves() {
        Em3Project project = SalvoPreset.create();
        RegionPartModel arm = new RegionPartModel();
        arm.id = "arm";
        arm.label = "Arm";
        arm.suffix = "-arm";
        arm.progress = TextNode.valueOf("warmup");
        arm.upsertKey("warmup", "x", 0f, 0f);
        arm.upsertKey("warmup", "x", 0.33f, 10f);
        arm.upsertKey("warmup", "x", 0.66f, 10f);
        arm.upsertKey("warmup", "x", 1f, 0f);
        arm.upsertKey("warmup", "y", 0f, 0f);
        arm.upsertKey("warmup", "y", 0.33f, 0f);
        arm.upsertKey("warmup", "y", 0.66f, -10f);
        arm.upsertKey("warmup", "y", 1f, -10f);
        project.turret.parts.add(arm);
        ExportReport report = new ExportReport();
        String json = ContentExporter.export(project, report);
        assertTrue(json.contains("\"op\" : \"compress\""));
        assertTrue(json.contains("\"x\" : 10"));
        assertTrue(json.contains("\"x\" : -10"));
        assertTrue(json.contains("\"y\" : -10"));
        assertFalse(json.contains("\"animations\""));
        assertTrue(report.ok(), report.errors.toString());
    }

    @Test
    void stepInterpolationIsReportedAndNotExportedAsLinear() {
        Em3Project project = SalvoPreset.create();
        RegionPartModel arm = new RegionPartModel();
        arm.id = "arm";
        arm.label = "Arm";
        arm.suffix = "-arm";
        arm.progress = TextNode.valueOf("warmup");
        arm.upsertKey("warmup", "x", 0f, 0f);
        Keyframe end = new Keyframe(1f, 8f);
        end.interpolation = "step";
        arm.channelOrCreate("warmup").trackOrCreate("x").keys.add(end);
        project.turret.parts.add(arm);
        ExportReport report = new ExportReport();
        String json = ContentExporter.export(project, report);
        assertTrue(report.errors.stream().anyMatch(error -> error.contains("step")));
        assertFalse(json.contains("\"x\" : 8"));
    }

    @Test
    void undoRestoresPartAndKeyframeEdits() {
        var turret = SalvoPreset.turret();
        History history = new History();
        RegionPartModel barrel = turret.parts.stream().filter(part -> "barrel".equals(part.id)).findFirst().orElseThrow();
        var before = History.capture(turret);
        barrel.x = 4f;
        barrel.upsertKey("recoil", "y", 0.2f, -1f);
        history.pushNow(turret, before);
        assertTrue(history.undo(turret));
        barrel = turret.parts.stream().filter(part -> "barrel".equals(part.id)).findFirst().orElseThrow();
        assertEquals(0f, barrel.x, 0.001f);
        assertFalse(barrel.hasTrack("recoil", "y") && barrel.channel("recoil").track("y").keys.stream().anyMatch(key -> Math.abs(key.time - 0.2f) < 0.001f && key.value == -1f));
        assertTrue(history.redo(turret));
        barrel = turret.parts.stream().filter(part -> "barrel".equals(part.id)).findFirst().orElseThrow();
        assertEquals(4f, barrel.x, 0.001f);
    }

    @Test
    void mirroredDragFollowsThePointer() {
        assertEquals(1f, RegionMath.moveXDelta(1f, 0f, 90f, 1f), 0.02f);
        assertEquals(-1f, RegionMath.moveXDelta(1f, 0f, 90f, -1f), 0.02f);
        assertEquals(1f, RegionMath.moveYDelta(0f, 1f, 90f, -1f), 0.02f);
        assertEquals(90f, RegionMath.rotationDelta(1f, 0f, 0f, 1f), 0.02f);
        assertEquals(15f, RegionMath.snap(16f, 15f), 0.001f);
    }

    @Test
    void shadowOffsetStaysOnWorldAxes() {
        var turret = SalvoPreset.turret();
        PartParams params = new PartParams();
        params.rotation = 90f;
        SpritePlacement shadow = shadowAt(turret, params, null);
        float elevation = MindustryV160.elevationFor(turret.size, turret.elevation);
        assertEquals(-elevation, shadow.centerX, 0.001f);
        assertEquals(-elevation, shadow.centerY, 0.001f);
        assertEquals("shadow", shadow.role);
        params.rotation = 10f;
        SpritePlacement turned = shadowAt(turret, params, null);
        assertEquals(shadow.centerX, turned.centerX, 0.001f);
        assertEquals(shadow.centerY, turned.centerY, 0.001f);
        assertEquals(params.rotation - 90f, turned.rotation, 0.001f);
        ShadowDebug debug = new ShadowDebug();
        debug.customOffsetX = 2f;
        debug.customOffsetY = -3f;
        debug.opacity = 0.5f;
        SpritePlacement custom = shadowAt(turret, params, debug);
        assertEquals(2f, custom.centerX, 0.001f);
        assertEquals(-3f, custom.centerY, 0.001f);
        assertEquals(0.5f, custom.a, 0.001f);
        assertTrue(shadowAt(turret, params, null).role.equals("shadow"));
        assertTrue(RegionMath.compose(turret, params, 0f, MISSING, null, null).stream().anyMatch(p -> "outline".equals(p.role)));
    }

    @Test
    void testModeAimsFiresAndRespectsTheFireRate() {
        TestPreview test = new TestPreview();
        test.active = true;
        test.fireRate = 2f;
        test.rotationSpeed = 180f;
        test.triggerHeld = true;
        PartParams params = new PartParams();
        params.rotation = 90f;
        params.warmup = 1f;
        test.aimDegrees = 0f;
        test.step(0.1f, params);
        assertTrue(params.rotation < 90f);
        assertEquals(1, test.shots);
        test.step(0.4f, params);
        assertEquals(1, test.shots);
        test.step(0.2f, params);
        assertEquals(2, test.shots);
        float frozen = params.rotation;
        test.frozen = true;
        test.step(1f, params);
        assertEquals(frozen, params.rotation, 0.001f);
        test.frozen = false;
        test.triggerHeld = false;
        params.recoil = 0f;
        params.warmup = 0.4f;
        test.step(0.05f, params);
        assertEquals("cooling", test.phase);
    }

    @Test
    void presetLinkDoesNotReplaceExistingSprites() {
        Em3Project project = SalvoPreset.create();
        project.sprites.put("salvo-barrel", "/keep/salvo-barrel.png");
        SalvoAssets.ensure(project);
        assertEquals("/keep/salvo-barrel.png", project.sprites.get("salvo-barrel"));
    }

    private static SpritePlacement shadowAt(em3.project.TurretContent turret, PartParams params, ShadowDebug debug) {
        return RegionMath.compose(turret, params, 0f, MISSING, null, debug).stream()
                .filter(placement -> "shadow".equals(placement.role))
                .findFirst()
                .orElseThrow();
    }
}
