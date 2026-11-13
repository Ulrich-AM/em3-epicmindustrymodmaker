package em3.app;

import em3.animation.PartParams;
import em3.animation.Playback;
import em3.animation.RegionMath;
import em3.animation.SpritePlacement;
import em3.assets.SpriteLibrary;
import em3.export.ContentExporter;
import em3.export.ExportReport;
import em3.mindustry.MindustryV160;
import em3.presets.SalvoPreset;
import em3.project.Em3Project;
import em3.project.PartMoveModel;
import em3.project.ProjectStore;
import em3.project.RegionPartModel;
import em3.project.SpriteSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Application state shared by the panels. No OpenGL and no ImGui types. */
public final class EditorSession {
    public Em3Project project = SalvoPreset.create();
    public final PartParams params = new PartParams();
    public final Playback playback = new Playback();
    public final Overlays overlays = new Overlays();
    public final EditorLog log = new EditorLog();
    public final SpriteLibrary sprites = new SpriteLibrary();
    public final List<SpritePlacement> placements = new ArrayList<>();
    public final List<SpritePlacement> restPlacements = new ArrayList<>();
    public final ExportReport report = new ExportReport();
    public String exportText = "";
    public String selection = "part/barrel";
    public boolean viewportMaximized;
    public boolean resetLayout;
    public float editorTicks;

    public float cameraX;
    public float cameraY;
    public float zoom = 18f;

    public EditorSession() {
        params.rotation = 90f;
        refresh();
        log.info("EM3 v0.1.0 — Salvo preset. Sprites are not bundled.");
        log.info("Target Mindustry " + MindustryV160.VERSION_LABEL + ".");
    }

    public void refresh() {
        report.errors.clear();
        report.warnings.clear();
        report.notes.clear();
        placements.clear();
        placements.addAll(RegionMath.compose(project.turret, params, editorTicks, this::spriteSize, report));
        PartParams rest = params.copy();
        rest.warmup = 0f;
        rest.reload = 0f;
        rest.smoothReload = 0f;
        rest.heat = 0f;
        rest.recoil = 0f;
        rest.charge = 0f;
        restPlacements.clear();
        restPlacements.addAll(RegionMath.compose(project.turret, rest, editorTicks, this::spriteSize, null));
        exportText = ContentExporter.export(project, report);
    }

    public SpriteSize spriteSize(String region) {
        if (region != null && region.endsWith("-outline")) {
            String source = region.substring(0, region.length() - "-outline".length());
            SpriteSize sized = sprites.size(source);
            if (sized.found()) {
                return SpriteSize.generated(sized.width(), sized.height(), source);
            }
        }
        return sprites.size(region);
    }

    public void reloadSprites() {
        sprites.reload(project);
        refresh();
        if (sprites.failures().isEmpty() && !project.sprites.isEmpty()) {
            log.info("Loaded " + project.sprites.size() + " sprite references.");
        } else if (!sprites.failures().isEmpty()) {
            log.error("Missing sprites: " + String.join(", ", sprites.failures().keySet()));
        }
    }

    public RegionPartModel selectedPart() {
        String id = partIdFromSelection();
        return id == null ? null : project.turret.find(id);
    }

    public String partIdFromSelection() {
        if (selection == null || !selection.startsWith("part/")) {
            return null;
        }
        String rest = selection.substring("part/".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    public int selectedSide() {
        if (selection != null && selection.contains("/side/")) {
            String tail = selection.substring(selection.lastIndexOf('/') + 1);
            try {
                return Integer.parseInt(tail);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    public SpritePlacement selectedPlacement() {
        for (SpritePlacement placement : placements) {
            if ("region".equals(placement.role) && selection.equals(placement.selectionKey)) {
                return placement;
            }
            if (selection.endsWith("/heat") && "heat".equals(placement.role) && placement.partId.equals(partIdFromSelection())) {
                return placement;
            }
            if ("base".equals(selection) && "base".equals(placement.role)) {
                return placement;
            }
            if ("preview".equals(selection) && "shadow".equals(placement.role)) {
                return placement;
            }
        }
        return null;
    }

    public void touch() {
        project.dirty = true;
        refresh();
    }

    public void newSalvo() {
        project = SalvoPreset.create();
        selection = "part/barrel";
        playback.reset(params);
        params.rotation = 90f;
        sprites.reload(project);
        refresh();
        log.info("New Salvo project. Save it to create the project folder.");
    }

    public void save(Path directory) throws IOException {
        ProjectStore.save(project, directory);
        remember(directory);
        log.info("Saved " + directory.resolve(ProjectStore.FILE_NAME));
        reloadSprites();
    }

    public void open(Path directory) throws IOException {
        project = ProjectStore.open(directory);
        remember(directory);
        selection = project.turret.parts.isEmpty() ? "base" : "part/" + project.turret.parts.get(0).id;
        reloadSprites();
        log.info("Opened " + project.name + " (" + project.mindustryVersion + ")");
    }

    public void importFolder(Path root) throws IOException {
        var found = SpriteLibrary.scan(root);
        List<String> missing = SpriteLibrary.importRegions(project, found, MindustryV160.SALVO_REGIONS);
        if (project.directory != null) {
            ProjectStore.save(project, project.directory);
        }
        reloadSprites();
        if (missing.isEmpty()) {
            log.info("Imported Salvo sprites from " + root);
        } else {
            log.error("Import did not find: " + String.join(", ", missing));
        }
    }

    public void downloadOfficial() {
        try {
            Path cache = SpriteLibrary.userCache();
            List<String> written = SpriteLibrary.downloadOfficial(cache);
            importFolder(cache);
            log.info("Downloaded " + written.size() + " official v160.7 sprites into " + cache);
        } catch (IOException e) {
            log.error("Download failed: " + e.getMessage());
        }
    }

    public void exportTo(Path file) throws IOException {
        refresh();
        Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
        Files.writeString(file, exportText);
        log.info("Exported " + file);
    }

    private void remember(Path directory) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".em3");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("session.json"),
                    "{\"lastProject\":\"" + directory.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}\n");
        } catch (IOException ignored) {
            // Session memory is optional.
        }
    }

    public static Path lastProject() {
        Path file = Path.of(System.getProperty("user.home"), ".em3", "session.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file);
            int i = text.indexOf("\"lastProject\"");
            if (i < 0) {
                return null;
            }
            int q = text.indexOf('"', text.indexOf(':', i) + 1);
            int e = text.indexOf('"', q + 1);
            if (q < 0 || e < 0) {
                return null;
            }
            return Path.of(text.substring(q + 1, e).replace("\\\\", "\\"));
        } catch (Exception e) {
            return null;
        }
    }

    public void renameSelected(String name) {
        RegionPartModel part = selectedPart();
        if (part == null || name == null || name.isBlank()) {
            return;
        }
        part.label = name.trim();
        touch();
    }

    public void addMove() {
        RegionPartModel part = selectedPart();
        if (part == null) {
            return;
        }
        PartMoveModel move = new PartMoveModel();
        move.progress = com.fasterxml.jackson.databind.node.TextNode.valueOf("recoil");
        part.moves.add(move);
        touch();
    }

    public void removeMove(int index) {
        RegionPartModel part = selectedPart();
        if (part == null || index < 0 || index >= part.moves.size()) {
            return;
        }
        part.moves.remove(index);
        touch();
    }
}
