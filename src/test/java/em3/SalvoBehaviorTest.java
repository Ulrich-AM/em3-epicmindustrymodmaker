package em3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import em3.animation.PartParams;
import em3.animation.Playback;
import em3.animation.ProgressEval;
import em3.animation.RegionMath;
import em3.animation.SpritePlacement;
import em3.assets.OutlineImages;
import em3.assets.SpriteLibrary;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalvoBehaviorTest {
    private static SpriteSize sized(String name) {
        if (name != null && name.endsWith("-outline")) {
            return SpriteSize.generated(64, 64, name);
        }
        return SpriteSize.of(64, 64, name);
    }

    private static SpritePlacement region(List<SpritePlacement> list, String key) {
        return list.stream()
                .filter(p -> "region".equals(p.role) && key.equals(p.selectionKey))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void salvoNumbersMatchBlocks() {
        var turret = SalvoPreset.turret();
        assertEquals("salvo", turret.name);
        assertEquals(2, turret.size);
        assertEquals(0f, turret.blockRecoil);
        RegionPartModel side = turret.find("side");
        RegionPartModel barrel = turret.find("barrel");
        assertEquals("-side", side.suffix);
        assertTrue(side.mirror);
        assertEquals(0.6f, side.moveX);
        assertEquals(-15f, side.moveRot);
        assertEquals(0.001f, side.layerOffset);
        assertEquals("warmup", side.progress.asText());
        assertEquals(1, side.moves.size());
        assertEquals("recoil", side.moves.get(0).progress.asText());
        assertEquals(0.5f, side.moves.get(0).x);
        assertEquals(-0.5f, side.moves.get(0).y);
        assertEquals(-8f, side.moves.get(0).rot);
        assertEquals("-barrel", barrel.suffix);
        assertFalse(barrel.mirror);
        assertEquals("recoil", barrel.progress.asText());
        assertEquals(-2.5f, barrel.moveY);
    }

    @Test
    void warmupAndRecoilMoveTheRealParts() {
        PartParams params = new PartParams();
        params.rotation = 90f;
        params.warmup = 1f;
        params.recoil = 0f;
        List<SpritePlacement> posed = RegionMath.compose(SalvoPreset.turret(), params, 0f, SalvoBehaviorTest::sized, null);
        SpritePlacement right = region(posed, "part/side/side/0");
        SpritePlacement left = region(posed, "part/side/side/1");
        assertNear(0.6f, right.centerX);
        assertNear(0f, right.centerY);
        assertNear(-15f, right.rotation);
        assertNear(-0.6f, left.centerX);
        assertNear(0f, left.centerY);
        assertNear(15f, left.rotation);
        assertEquals("salvo-side-r", right.sprite);
        assertEquals("salvo-side-l", left.sprite);

        params.recoil = 1f;
        posed = RegionMath.compose(SalvoPreset.turret(), params, 0f, SalvoBehaviorTest::sized, null);
        right = region(posed, "part/side/side/0");
        left = region(posed, "part/side/side/1");
        SpritePlacement barrel = region(posed, "part/barrel");
        assertNear(1.1f, right.centerX);
        assertNear(-0.5f, right.centerY);
        assertNear(-23f, right.rotation);
        assertNear(-1.1f, left.centerX);
        assertNear(-0.5f, left.centerY);
        assertNear(23f, left.rotation);
        assertNear(0f, barrel.centerX);
        assertNear(-2.5f, barrel.centerY);
        assertNear(0f, barrel.rotation);

        params.rotation = 0f;
        posed = RegionMath.compose(SalvoPreset.turret(), params, 0f, SalvoBehaviorTest::sized, null);
        barrel = region(posed, "part/barrel");
        assertNear(-2.5f, barrel.centerX);
        assertNear(0f, barrel.centerY);
    }

    @Test
    void hidingAPartDoesNotClearItsExportedOutline() {
        Em3Project project = SalvoPreset.create();
        project.turret.find("barrel").editorVisible = false;
        project.turret.find("barrel").outline = true;
        List<SpritePlacement> posed = RegionMath.compose(project.turret, new PartParams(), 0f, SalvoBehaviorTest::sized, null);
        assertTrue(posed.stream().noneMatch(p -> "barrel".equals(p.partId)));
        ExportReport report = new ExportReport();
        String json = ContentExporter.export(project, report);
        assertTrue(json.contains("\"outline\" : true"));
        assertFalse(json.contains("editorVisible"));
        assertTrue(report.notes.stream().anyMatch(n -> n.contains("hidden in the editor")));
    }

    @Test
    void overlayToggleIsNotAnExportedField() {
        ExportReport report = new ExportReport();
        String json = ContentExporter.export(SalvoPreset.create(), report);
        assertFalse(json.contains("showOutlines"));
        assertTrue(json.contains("\"suffix\" : \"-side\""));
        assertTrue(json.contains("\"moveY\" : -2.5"));
        assertTrue(json.contains("\"mirror\" : true"));
        assertTrue(json.contains("\"type\" : \"DrawTurret\""));
        assertTrue(report.ok());
    }

    @Test
    void progressExpressionsAndUnknownFieldsSurvive(@TempDir Path dir) throws Exception {
        PartParams params = new PartParams();
        params.recoil = 0.25f;
        ObjectNode expr = ProjectStore.mapper().createObjectNode();
        expr.put("type", "recoil");
        expr.put("op", "inv");
        assertNear(0.75f, ProgressEval.eval(expr, params, 0f, true, null));

        ObjectNode slope = ProjectStore.mapper().createObjectNode();
        slope.put("type", "warmup");
        slope.put("op", "slope");
        params.warmup = 0.25f;
        assertNear(0.5f, ProgressEval.eval(slope, params, 0f, true, null));

        ExportReport report = new ExportReport();
        assertEquals(0f, ProgressEval.eval(TextNode.valueOf("not-a-channel"), params, 0f, true, report));
        assertFalse(report.errors.isEmpty());

        Em3Project project = SalvoPreset.create();
        ObjectNode extra = ProjectStore.mapper().createObjectNode();
        extra.put("customNote", "keep-me");
        project.turret.find("barrel").extras = extra;
        project.turret.extras = ProjectStore.mapper().createObjectNode().put("requirements", "copper/80");
        ProjectStore.save(project, dir);
        Em3Project opened = ProjectStore.open(dir);
        assertEquals("keep-me", opened.turret.find("barrel").extras.get("customNote").asText());
        assertEquals("copper/80", opened.turret.extras.get("requirements").asText());
        assertEquals(0.6f, opened.turret.find("side").moveX);
        String exported = ContentExporter.export(opened, new ExportReport());
        assertTrue(exported.contains("customNote"));
        assertTrue(exported.contains("requirements"));
        assertFalse(exported.contains("\"id\""));
    }

    @Test
    void saveReopenKeepsSpriteReferences(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("incoming");
        Files.createDirectories(source);
        writePng(source.resolve("salvo-barrel.png"), 8, 4);
        Em3Project project = SalvoPreset.create();
        var found = SpriteLibrary.scan(source);
        List<String> missing = SpriteLibrary.importRegions(project, found, "salvo-barrel", "block-2");
        assertEquals(List.of("block-2"), missing);
        Path projectDir = dir.resolve("proj");
        ProjectStore.save(project, projectDir);
        assertTrue(Files.isRegularFile(projectDir.resolve("assets/salvo-barrel.png")));
        assertTrue(Files.isDirectory(projectDir.resolve("turrets")));
        assertTrue(Files.isDirectory(projectDir.resolve("units")));
        Em3Project opened = ProjectStore.open(projectDir);
        assertEquals("assets/salvo-barrel.png", opened.sprites.get("salvo-barrel"));
        assertEquals(MindustryV160.VERSION_TAG, opened.mindustryVersion);
        SpriteLibrary library = new SpriteLibrary();
        library.reload(opened);
        assertTrue(library.has("salvo-barrel"));
        assertEquals(8, library.size("salvo-barrel").width());
        assertTrue(ProjectStore.missingSprites(opened).isEmpty());
    }

    @Test
    void outlineUsesRadiusNotAOnePixelBorder() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(10, 10, 0xFFFFFFFF);
        BufferedImage outlined = OutlineImages.outline(image, 0xFF404049, 4);
        assertEquals(0xFF404049, outlined.getRGB(10, 12));
        assertEquals(0xFF404049, outlined.getRGB(14, 10));
        assertEquals(0x00000000, outlined.getRGB(16, 10));
        assertEquals(0xFFFFFFFF, outlined.getRGB(10, 10));
    }

    @Test
    void playbackScrubFillsPartParams() {
        PartParams params = new PartParams();
        Playback playback = new Playback();
        playback.scrub(0f, params);
        assertEquals(1f, params.recoil, 0.001f);
        assertEquals(1f, params.reload, 0.001f);
        playback.scrub(1f, params);
        assertEquals(0f, params.recoil, 0.001f);
        assertEquals(0f, params.reload, 0.001f);
    }

    @Test
    void exportedProgressRejectsUnknownNames() {
        Em3Project project = SalvoPreset.create();
        project.turret.find("barrel").progress = TextNode.valueOf("made-up");
        ExportReport report = new ExportReport();
        ContentExporter.export(project, report);
        assertFalse(report.ok());
    }

    @Test
    void mirrorSideSpritesStayDistinct() {
        List<SpritePlacement> posed = RegionMath.compose(SalvoPreset.turret(), new PartParams(), 0f, SalvoBehaviorTest::sized, null);
        long outlines = posed.stream().filter(p -> "outline".equals(p.role) && p.sprite.contains("side")).count();
        assertEquals(2, outlines);
        assertTrue(posed.stream().anyMatch(p -> "salvo-side-r-outline".equals(p.sprite)));
        assertTrue(posed.stream().anyMatch(p -> "salvo-side-l-outline".equals(p.sprite)));
        JsonNode tree = read(ContentExporter.export(SalvoPreset.create(), new ExportReport()));
        assertEquals(2, tree.get("drawer").get("parts").size());
    }

    private static JsonNode read(String json) {
        try {
            return ProjectStore.mapper().readTree(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void writePng(Path path, int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        ImageIO.write(image, "png", path.toFile());
    }

    private static void assertNear(float expected, float actual) {
        assertEquals(expected, actual, 0.001f);
    }
}
