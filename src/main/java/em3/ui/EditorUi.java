package em3.ui;

import em3.animation.SpritePlacement;
import em3.app.EditorSession;
import em3.app.Overlays;
import em3.export.ExportReport;
import em3.mindustry.MindustryV160;
import em3.project.Em3Project;
import em3.project.PartMoveModel;
import em3.project.ProjectStore;
import em3.project.RegionPartModel;
import em3.project.TurretContent;
import em3.viewport.SceneRenderer;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_M;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;

/**
 * Dear ImGui docking workspace. The scene is a texture produced by {@link SceneRenderer} in the same OpenGL context.
 */
public final class EditorUi {
    private final long window;
    private final EditorSession session;
    private final SceneRenderer renderer;
    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
    private final ImString pathBuffer = new ImString(512);
    private final ImString nameBuffer = new ImString(128);
    private final ImString progressBuffer = new ImString(256);
    private final ImString jsonBuffer = new ImString(64 * 1024);
    private final float[] scratch = new float[1];
    private String prompt = "";
    private boolean showAbout;
    private boolean focusAnimation;
    private boolean ctrlSHeld;
    private boolean ctrlOHeld;
    private boolean spaceHeld;
    private boolean keyFHeld;
    private boolean ctrlShiftMHeld;
    private int dragAxis;
    private float dragOriginX;
    private float dragOriginY;
    private float dragStartValue;
    private String lastJson = "";
    private final Path iniPath = Path.of(System.getProperty("user.home"), ".em3", "workspace.ini");
    private final Path iniBackup = Path.of(System.getProperty("user.home"), ".em3", "workspace-user.ini");

    public EditorUi(long window, EditorSession session, SceneRenderer renderer) {
        this.window = window;
        this.session = session;
        this.renderer = renderer;
    }

    public void init() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(iniPath.toString());
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        // Platform viewports are left off. With them enabled, imgui-java moved the docked panels
        // into extra GLFW windows and the main viewport submitted no geometry under llvmpipe.
        // Docking, splits, and saved layouts stay in the primary window.
        io.setConfigWindowsMoveFromTitleBarOnly(true);
        applyTheme();
        glfw.init(window, true);
        gl3.init("#version 150");
        try {
            Files.createDirectories(iniPath.getParent());
        } catch (Exception ignored) {
            // Layout persistence is optional.
        }
    }

    public void frame() {
        shortcuts();
        glfw.newFrame();
        ImGui.newFrame();
        dock();
        if (!session.viewportMaximized) {
            hierarchy();
            inspector();
            animation();
            json();
            console();
            assets();
            if (focusAnimation) {
                ImGui.setWindowFocus("Animation");
                focusAnimation = false;
            }
        }
        scene();
        pathModal();
        if (showAbout) {
            about();
        }
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());
        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            long backup = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(backup);
        }
    }

    public void dispose() {
        gl3.dispose();
        glfw.dispose();
        ImGui.destroyContext();
    }

    private void shortcuts() {
        boolean ctrl = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS;
        boolean shift = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
        boolean ctrlS = ctrl && glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS;
        boolean ctrlO = ctrl && glfwGetKey(window, GLFW_KEY_O) == GLFW_PRESS;
        boolean space = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
        boolean keyF = glfwGetKey(window, GLFW_KEY_F) == GLFW_PRESS;
        boolean ctrlShiftM = ctrl && shift && glfwGetKey(window, GLFW_KEY_M) == GLFW_PRESS;
        if (ctrlS && !ctrlSHeld) {
            save();
        }
        if (ctrlO && !ctrlOHeld) {
            askPath("open", "Open project folder");
        }
        if (space && !spaceHeld && !ImGui.getIO().getWantTextInput()) {
            session.playback.playing = !session.playback.playing;
        }
        if (keyF && !keyFHeld && !ImGui.getIO().getWantTextInput()) {
            resetCamera();
        }
        if (ctrlShiftM && !ctrlShiftMHeld) {
            toggleMaximize();
        }
        ctrlSHeld = ctrlS;
        ctrlOHeld = ctrlO;
        spaceHeld = space;
        keyFHeld = keyF;
        ctrlShiftMHeld = ctrlShiftM;
    }

    private void dock() {
        ImGuiIO io = ImGui.getIO();
        int flags = ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.NoBackground;
        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(io.getDisplaySizeX(), io.getDisplaySizeY());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.begin("DockHost", flags);
        ImGui.popStyleVar(3);
        int dockId = ImGui.getID("EM3Dock");
        imgui.internal.ImGuiDockNode existing = imgui.internal.ImGui.dockBuilderGetNode(dockId);
        if (session.resetLayout || existing == null || existing.isNotValidPtr()) {
            buildLayout(dockId, io.getDisplaySizeX(), io.getDisplaySizeY());
            session.resetLayout = false;
            focusAnimation = true;
        }
        ImGui.dockSpace(dockId, 0, 0, ImGuiDockNodeFlags.PassthruCentralNode);
        menu();
        ImGui.end();
    }

    private void buildLayout(int dockId, float width, float height) {
        imgui.internal.ImGui.dockBuilderRemoveNode(dockId);
        imgui.internal.ImGui.dockBuilderAddNode(dockId, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockId, width, height);
        ImInt left = new ImInt();
        ImInt rest = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(dockId, ImGuiDir.Left, 0.18f, left, rest);
        ImInt right = new ImInt();
        ImInt center = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(rest.get(), ImGuiDir.Right, 0.30f, right, center);
        ImInt bottom = new ImInt();
        ImInt scene = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Down, 0.36f, bottom, scene);
        imgui.internal.ImGui.dockBuilderDockWindow("Hierarchy", left.get());
        imgui.internal.ImGui.dockBuilderDockWindow("Inspector", right.get());
        imgui.internal.ImGui.dockBuilderDockWindow("Scene", scene.get());
        imgui.internal.ImGui.dockBuilderDockWindow("Animation", bottom.get());
        imgui.internal.ImGui.dockBuilderDockWindow("JSON Output", bottom.get());
        imgui.internal.ImGui.dockBuilderDockWindow("Console", bottom.get());
        imgui.internal.ImGui.dockBuilderDockWindow("Assets", bottom.get());
        imgui.internal.ImGui.dockBuilderFinish(dockId);
    }

    private void menu() {
        if (!ImGui.beginMenuBar()) {
            return;
        }
        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("New Salvo Project")) {
                session.newSalvo();
                renderer.forgetTextures();
            }
            if (ImGui.menuItem("Open...", "Ctrl+O")) {
                askPath("open", "Open project folder");
            }
            if (ImGui.menuItem("Save", "Ctrl+S")) {
                save();
            }
            if (ImGui.menuItem("Save As...")) {
                askPath("save", "Save project folder");
            }
            if (ImGui.menuItem("Export JSON...")) {
                askPath("export", "Export JSON file");
            }
            ImGui.separator();
            if (ImGui.menuItem("Exit")) {
                glfwSetWindowShouldClose(window, true);
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Edit")) {
            if (ImGui.menuItem("Reset Animation")) {
                session.playback.reset(session.params);
                session.params.rotation = 90f;
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("View")) {
            overlayItem("Show outlines", "outlines");
            overlayItem("Show part names", "names");
            overlayItem("Show pivot points", "pivots");
            overlayItem("Show bounding boxes", "bounds");
            overlayItem("Show symmetry axis", "symmetry");
            overlayItem("Show heat textures", "heat");
            overlayItem("Show sprite transparency", "transparency");
            overlayItem("Show part layer order", "layerOrder");
            overlayItem("Show animation movement paths", "paths");
            overlayItem("Show turret base", "base");
            overlayItem("Show world grid", "grid");
            ImGui.separator();
            ImGui.text("Outline preview");
            scratch[0] = session.overlays.outlineRadius;
            ImGui.setNextItemWidth(120);
            if (ImGui.dragFloat("Radius##outline", scratch, 0.1f, 1f, 12f, "%.0f")) {
                session.overlays.outlineRadius = Math.round(scratch[0]);
                renderer.forgetTextures();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Window")) {
            if (ImGui.menuItem("Reset Layout")) {
                resetLayout();
            }
            if (ImGui.menuItem(session.viewportMaximized ? "Restore Workspace" : "Maximize Viewport", "Ctrl+Shift+M")) {
                toggleMaximize();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Tools")) {
            if (ImGui.menuItem("Download official Salvo sprites")) {
                session.downloadOfficial();
                renderer.forgetTextures();
            }
            if (ImGui.menuItem("Import sprites from folder...")) {
                askPath("import", "Mindustry source or install folder");
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Help")) {
            if (ImGui.menuItem("About EM3")) {
                showAbout = true;
            }
            ImGui.endMenu();
        }
        ImGui.endMenuBar();
    }

    private void overlayItem(String label, String field) {
        Overlays overlays = session.overlays;
        boolean value = switch (field) {
            case "outlines" -> overlays.outlines;
            case "names" -> overlays.names;
            case "pivots" -> overlays.pivots;
            case "bounds" -> overlays.bounds;
            case "symmetry" -> overlays.symmetry;
            case "heat" -> overlays.heat;
            case "transparency" -> overlays.transparency;
            case "layerOrder" -> overlays.layerOrder;
            case "paths" -> overlays.paths;
            case "base" -> overlays.base;
            default -> overlays.grid;
        };
        if (ImGui.menuItem(label, "", value)) {
            boolean next = !value;
            switch (field) {
                case "outlines" -> overlays.outlines = next;
                case "names" -> overlays.names = next;
                case "pivots" -> overlays.pivots = next;
                case "bounds" -> overlays.bounds = next;
                case "symmetry" -> overlays.symmetry = next;
                case "heat" -> overlays.heat = next;
                case "transparency" -> overlays.transparency = next;
                case "layerOrder" -> overlays.layerOrder = next;
                case "paths" -> overlays.paths = next;
                case "base" -> overlays.base = next;
                default -> overlays.grid = next;
            }
        }
    }

    private void hierarchy() {
        ImGui.begin("Hierarchy");
        TurretContent turret = session.project.turret;
        int flags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (ImGui.treeNodeEx(turret.name + "##root", flags)) {
            selectRow("Base", "base");
            selectRow("Main turret body", "preview");
            for (RegionPartModel part : turret.parts) {
                if (part.mirror) {
                    selectRow("Right side", "part/" + part.id + "/side/0");
                    selectRow("Left side", "part/" + part.id + "/side/1");
                } else {
                    selectRow(part.label, "part/" + part.id);
                }
                if (heatAvailable(part)) {
                    selectRow("Heat overlay", "part/" + part.id + "/heat");
                }
            }
            ImGui.treePop();
        }
        ImGui.end();
    }

    private boolean heatAvailable(RegionPartModel part) {
        String real = part.name != null && !part.name.isBlank()
                ? part.name
                : session.project.turret.name + part.suffix;
        String heat = real + "-heat";
        return session.sprites.has(heat) || session.project.sprites.containsKey(heat);
    }

    private void selectRow(String label, String key) {
        if (ImGui.selectable(label, key.equals(session.selection))) {
            session.selection = key;
            syncNameBuffer();
        }
    }

    private void inspector() {
        ImGui.begin("Inspector");
        if ("base".equals(session.selection)) {
            turretFields();
        } else if ("preview".equals(session.selection)) {
            ImGui.textWrapped("Main turret body is the shadow sprite. DrawTurret draws salvo-preview tinted with Pal.shadow. "
                    + "There is no raw salvo.png, so the colored body region is not drawn.");
            ImGui.text("Sprite");
            ImGui.sameLine();
            ImGui.text(session.project.turret.previewRegion);
        } else {
            RegionPartModel part = session.selectedPart();
            if (part == null) {
                ImGui.textWrapped("Select a part in the Hierarchy.");
            } else {
                partFields(part);
            }
        }
        SpritePlacement placement = session.selectedPlacement();
        if (placement != null) {
            ImGui.separator();
            ImGui.text("Evaluated transform");
            ImGui.text(String.format("World  %.3f, %.3f", placement.centerX, placement.centerY));
            ImGui.text(String.format("Draw rotation  %.2f", placement.rotation));
            ImGui.text(String.format("Layer  %.4f", placement.z));
            ImGui.text("Sprite  " + placement.sprite);
            if (placement.missing) {
                ImGui.textColored(1f, 0.55f, 0.35f, 1f, "Sprite file is missing");
            }
        }
        ImGui.end();
    }

    private void turretFields() {
        TurretContent turret = session.project.turret;
        ImGui.text("Turret");
        if (drag("Size", turret.size, 0.1f, 1f, 8f)) {
            turret.size = Math.round(scratch[0]);
            session.touch();
        }
        if (drag("Elevation", turret.elevation, 0.05f, -1f, 8f)) {
            turret.elevation = scratch[0];
            session.touch();
        }
        if (drag("Block recoil", turret.blockRecoil, 0.05f, 0f, 8f)) {
            turret.blockRecoil = scratch[0];
            session.touch();
        }
        if (drag("Recoil power", turret.recoilPow, 0.02f, 0.1f, 4f)) {
            turret.recoilPow = scratch[0];
            session.touch();
        }
        ImGui.textWrapped("Block recoil is Turret.recoil. Salvo sets it to 0. Part recoil is a separate progress channel.");
    }

    private void partFields(RegionPartModel part) {
        if (!part.label.equals(nameBuffer.get())) {
            syncNameBuffer();
        }
        ImGui.text("Label");
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##label", nameBuffer)) {
            part.label = nameBuffer.get().trim().isEmpty() ? part.label : nameBuffer.get().trim();
            session.touch();
        }
        ImGui.text("Sprite suffix");
        ImGui.text(part.suffix == null || part.suffix.isEmpty() ? "(none)" : part.suffix);
        if (part.name != null && !part.name.isBlank()) {
            ImGui.text("Sprite name  " + part.name);
        }
        if (drag("X", part.x, 0.02f, -16f, 16f)) {
            part.x = scratch[0];
            session.touch();
        }
        if (drag("Y", part.y, 0.02f, -16f, 16f)) {
            part.y = scratch[0];
            session.touch();
        }
        if (drag("Origin X", part.originX, 0.05f, -32f, 32f)) {
            part.originX = scratch[0];
            session.touch();
        }
        if (drag("Origin Y", part.originY, 0.05f, -32f, 32f)) {
            part.originY = scratch[0];
            session.touch();
        }
        if (drag("Rotation", part.rotation, 0.2f, -180f, 180f)) {
            part.rotation = scratch[0];
            session.touch();
        }
        if (drag("Scale X", part.xScl, 0.01f, -4f, 4f)) {
            part.xScl = scratch[0];
            session.touch();
        }
        if (drag("Scale Y", part.yScl, 0.01f, -4f, 4f)) {
            part.yScl = scratch[0];
            session.touch();
        }
        if (drag("Move X", part.moveX, 0.02f, -8f, 8f)) {
            part.moveX = scratch[0];
            session.touch();
        }
        if (drag("Move Y", part.moveY, 0.02f, -8f, 8f)) {
            part.moveY = scratch[0];
            session.touch();
        }
        if (drag("Move rotation", part.moveRot, 0.2f, -180f, 180f)) {
            part.moveRot = scratch[0];
            session.touch();
        }
        if (ImGui.checkbox("Mirror", part.mirror)) {
            part.mirror = !part.mirror;
            session.touch();
        }
        progressField("Progress", part);
        if (drag("Layer", part.layer, 0.1f, -1f, 120f)) {
            part.layer = scratch[0];
            session.touch();
        }
        if (drag("Layer offset", part.layerOffset, 0.0001f, -1f, 1f)) {
            part.layerOffset = scratch[0];
            session.touch();
        }
        if (ImGui.checkbox("Outline", part.outline)) {
            part.outline = !part.outline;
            session.touch();
        }
        ImGui.sameLine();
        ImGui.textDisabled("exported");
        if (drag("Outline layer offset", part.outlineLayerOffset, 0.0001f, -1f, 1f)) {
            part.outlineLayerOffset = scratch[0];
            session.touch();
        }
        ImGui.separator();
        ImGui.textDisabled("Editor only");
        if (ImGui.checkbox("Visible in editor", part.editorVisible)) {
            part.editorVisible = !part.editorVisible;
            session.touch();
        }
        ImGui.textWrapped("Hiding a part here does not change the exported outline flag. View > Show outlines is also preview-only.");
        ImGui.separator();
        ImGui.text("Part moves");
        for (int i = 0; i < part.moves.size(); i++) {
            PartMoveModel move = part.moves.get(i);
            ImGui.pushID(i);
            ImGui.text(move.progress == null ? "progress" : move.progress.toString());
            if (drag("Move X##m", move.x, 0.02f, -8f, 8f)) {
                move.x = scratch[0];
                session.touch();
            }
            if (drag("Move Y##m", move.y, 0.02f, -8f, 8f)) {
                move.y = scratch[0];
                session.touch();
            }
            if (drag("Move rotation##m", move.rot, 0.2f, -180f, 180f)) {
                move.rot = scratch[0];
                session.touch();
            }
            if (ImGui.button("Remove move")) {
                session.removeMove(i);
            }
            ImGui.popID();
        }
        if (ImGui.button("Add recoil move")) {
            session.addMove();
        }
        if (part.extras != null && !part.extras.isEmpty()) {
            ImGui.separator();
            ImGui.textWrapped("Preserved fields the inspector does not edit:");
            ImGui.textWrapped(part.extras.toString());
        }
    }

    private void progressField(String label, RegionPartModel part) {
        ImGui.text(label);
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##progress", progressBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String text = progressBuffer.get().trim();
            try {
                if (text.startsWith("{") || text.startsWith("[")) {
                    part.progress = ProjectStore.mapper().readTree(text);
                } else {
                    part.progress = ProjectStore.mapper().getNodeFactory().textNode(text);
                }
                session.touch();
            } catch (Exception e) {
                session.log.error("Progress value was not accepted: " + e.getMessage());
            }
        }
    }

    private boolean drag(String label, float value, float speed, float min, float max) {
        scratch[0] = value;
        ImGui.setNextItemWidth(-1);
        String caption = label.contains("##") ? label.substring(0, label.indexOf('#')) : label;
        ImGui.text(caption);
        return ImGui.dragFloat("##" + label, scratch, speed, min, max, "%.3f");
    }

    private void animation() {
        ImGui.begin("Animation");
        if (ImGui.button(session.playback.playing ? "Pause" : "Play")) {
            session.playback.playing = !session.playback.playing;
        }
        ImGui.sameLine();
        if (ImGui.button("Reset")) {
            session.playback.reset(session.params);
            session.params.rotation = 90f;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Loop", session.playback.loop)) {
            session.playback.loop = !session.playback.loop;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Reverse", session.playback.reverse)) {
            session.playback.reverse = !session.playback.reverse;
        }
        scratch[0] = session.playback.cycle;
        ImGui.setNextItemWidth(-1);
        if (ImGui.sliderFloat("Cycle", scratch, 0f, 1f, "%.3f")) {
            session.playback.scrub(scratch[0], session.params);
        }
        if (ImGui.beginTable("channels", 2)) {
            ImGui.tableNextColumn();
            if (drag("Speed", session.playback.speed, 0.02f, 0.05f, 4f)) {
                session.playback.speed = scratch[0];
            }
            channel("Warmup", "warmup");
            channel("Reload", "reload");
            channel("Recoil", "recoil");
            ImGui.tableNextColumn();
            channel("Charge", "charge");
            channel("Heat", "heat");
            if (drag("Turret rotation", session.params.rotation, 0.4f, -360f, 360f)) {
                session.params.rotation = scratch[0];
            }
            if (ImGui.checkbox("Spin with playback", session.playback.rotateWithPlayback)) {
                session.playback.rotateWithPlayback = !session.playback.rotateWithPlayback;
            }
            ImGui.endTable();
        }
        ImGui.textWrapped("Reload uses DrawTurret's scale: 1 is just fired. Editing a slider pauses the preview curve until Play, Reset, or the cycle scrubber.");
        ImGui.end();
    }

    private void channel(String label, String name) {
        float value = session.params.channel(name);
        scratch[0] = value;
        ImGui.setNextItemWidth(-1);
        ImGui.text(label);
        if (ImGui.sliderFloat("##" + name, scratch, 0f, 1f, "%.3f")) {
            session.playback.driveChannels = false;
            switch (name) {
                case "warmup" -> session.params.warmup = scratch[0];
                case "reload" -> {
                    session.params.reload = scratch[0];
                    session.params.smoothReload = scratch[0];
                }
                case "recoil" -> session.params.recoil = scratch[0];
                case "charge" -> session.params.charge = scratch[0];
                case "heat" -> session.params.heat = scratch[0];
                default -> {
                }
            }
        }
    }

    private void json() {
        ImGui.begin("JSON Output");
        if (ImGui.button("Copy")) {
            ImGui.setClipboardText(session.exportText);
            session.log.info("Copied JSON to the clipboard");
        }
        ImGui.sameLine();
        if (ImGui.button("Save output")) {
            askPath("export", "Export JSON file");
        }
        ExportReport report = session.report;
        for (String error : report.errors) {
            ImGui.textColored(1f, 0.45f, 0.4f, 1f, error);
        }
        for (String warning : report.warnings) {
            ImGui.textColored(0.95f, 0.78f, 0.35f, 1f, warning);
        }
        for (String note : report.notes) {
            ImGui.textDisabled(note);
        }
        if (!session.exportText.equals(lastJson)) {
            lastJson = session.exportText;
            jsonBuffer.set(lastJson);
        }
        float h = Math.max(80f, ImGui.getContentRegionAvailY());
        ImGui.inputTextMultiline("##json", jsonBuffer, -1, h, ImGuiInputTextFlags.ReadOnly);
        ImGui.end();
    }

    private void console() {
        ImGui.begin("Console");
        for (String line : session.log.lines) {
            if (line.startsWith("Error:")) {
                ImGui.textColored(1f, 0.45f, 0.4f, 1f, line);
            } else {
                ImGui.textWrapped(line);
            }
        }
        if (ImGui.getScrollY() >= ImGui.getScrollMaxY()) {
            ImGui.setScrollHereY(1f);
        }
        ImGui.end();
    }

    private void assets() {
        ImGui.begin("Assets");
        ImGui.text("Mindustry " + session.project.mindustryVersion);
        if (session.project.directory != null) {
            ImGui.textWrapped(session.project.directory.toString());
        } else {
            ImGui.textDisabled("Project has not been saved yet");
        }
        if (ImGui.button("Download v160.7 Salvo sprites")) {
            session.downloadOfficial();
            renderer.forgetTextures();
        }
        ImGui.sameLine();
        if (ImGui.button("Import folder")) {
            askPath("import", "Mindustry source or install folder");
        }
        if (session.project.sprites.isEmpty()) {
            ImGui.textColored(1f, 0.62f, 0.35f, 1f, "No sprites are linked.");
            ImGui.textWrapped("EM3 does not ship Mindustry artwork. Use Download, or import a checkout of Anuken/Mindustry at tag "
                    + MindustryV160.VERSION_TAG + ". The importer looks for salvo-preview, salvo-barrel, salvo-barrel-heat, "
                    + "salvo-side-l, salvo-side-r, and bases/block-2.");
        }
        for (var entry : session.project.sprites.entrySet()) {
            boolean ok = session.sprites.has(entry.getKey());
            if (ok) {
                ImGui.text(entry.getKey());
            } else {
                ImGui.textColored(1f, 0.45f, 0.4f, 1f, entry.getKey() + "  missing");
            }
            ImGui.sameLine();
            ImGui.textDisabled(entry.getValue());
        }
        ImGui.end();
    }

    private void scene() {
        int flags = session.viewportMaximized ? ImGuiWindowFlags.NoCollapse : 0;
        if (session.viewportMaximized) {
            ImGui.setNextWindowPos(0, ImGui.getFrameHeight(), ImGuiCond.Always);
            ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY() - ImGui.getFrameHeight(), ImGuiCond.Always);
        }
        ImGui.begin("Scene", flags);
        boolean missing = session.project.sprites.isEmpty() || !session.sprites.failures().isEmpty();
        if (missing) {
            ImGui.textColored(1f, 0.62f, 0.35f, 1f, "Salvo sprites are not loaded.");
            ImGui.textWrapped("Tools > Download official Salvo sprites, or import a Mindustry " + MindustryV160.VERSION_TAG
                    + " source tree. The preview stays empty until those PNGs are available.");
        }
        if (ImGui.button("Reset camera")) {
            resetCamera();
        }
        ImGui.sameLine();
        ImGui.textDisabled("Wheel zoom, middle-mouse pan, F reset");
        ImVec2 avail = ImGui.getContentRegionAvail();
        int w = Math.max(8, (int) avail.x);
        int h = Math.max(8, (int) avail.y);
        renderer.render(session, w, h);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImGui.image(renderer.textureId(), w, h, 0f, 1f, 1f, 0f);
        boolean hovered = ImGui.isItemHovered();
        float mx = ImGui.getIO().getMousePosX() - origin.x;
        float my = ImGui.getIO().getMousePosY() - origin.y;
        if (hovered) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0f) {
                float wx = worldX(mx, w);
                float wy = worldY(my, h);
                session.zoom = Math.max(4f, Math.min(96f, session.zoom * (float) Math.pow(1.12, wheel)));
                float viewW = w / session.zoom;
                float viewH = h / session.zoom;
                session.cameraX = wx - (mx / w - 0.5f) * viewW;
                session.cameraY = wy + (my / h - 0.5f) * viewH;
            }
            if (ImGui.isMouseDragging(2)) {
                session.cameraX -= ImGui.getIO().getMouseDeltaX() / session.zoom;
                session.cameraY += ImGui.getIO().getMouseDeltaY() / session.zoom;
            }
            handleSelection(mx, my, w, h);
        }
        if (session.overlays.names || session.overlays.layerOrder) {
            drawLabels(origin.x, origin.y, w, h);
        }
        ImGui.end();
    }

    private void handleSelection(float mx, float my, int w, int h) {
        float wx = worldX(mx, w);
        float wy = worldY(my, h);
        SpritePlacement selected = session.selectedPlacement();
        if (selected != null && "region".equals(selected.role) && ImGui.isMouseDown(0)) {
            if (dragAxis == 0 && ImGui.isMouseClicked(0)) {
                float sign = selected.side == 1 ? -1f : 1f;
                float[] axis = em3.animation.RegionMath.localAxis(session.params.rotation, sign);
                float len = 2.2f;
                if (near(wx, wy, selected.centerX + axis[0] * len, selected.centerY + axis[1] * len, 0.55f)) {
                    dragAxis = 1;
                    dragOriginX = wx;
                    dragOriginY = wy;
                    RegionPartModel part = session.selectedPart();
                    dragStartValue = part == null ? 0f : part.x;
                } else if (near(wx, wy, selected.centerX + axis[2] * len, selected.centerY + axis[3] * len, 0.55f)) {
                    dragAxis = 2;
                    dragOriginX = wx;
                    dragOriginY = wy;
                    RegionPartModel part = session.selectedPart();
                    dragStartValue = part == null ? 0f : part.y;
                }
            }
            if (dragAxis != 0) {
                RegionPartModel part = session.selectedPart();
                if (part != null) {
                    float sign = selected.side == 1 ? -1f : 1f;
                    float[] axis = em3.animation.RegionMath.localAxis(session.params.rotation, sign);
                    float dx = wx - dragOriginX;
                    float dy = wy - dragOriginY;
                    if (dragAxis == 1) {
                        part.x = dragStartValue + (dx * axis[0] + dy * axis[1]) * sign;
                    } else {
                        part.y = dragStartValue + dx * axis[2] + dy * axis[3];
                    }
                    session.project.dirty = true;
                }
                return;
            }
        } else {
            dragAxis = 0;
        }
        if (hoveredClick()) {
            String hit = null;
            float best = Float.NEGATIVE_INFINITY;
            for (SpritePlacement placement : session.placements) {
                if (!"region".equals(placement.role) || placement.missing) {
                    continue;
                }
                if (placement.contains(wx, wy) && placement.z >= best) {
                    best = placement.z;
                    hit = placement.selectionKey;
                }
            }
            if (hit != null) {
                session.selection = hit;
                syncNameBuffer();
            }
        }
    }

    private boolean hoveredClick() {
        return ImGui.isMouseClicked(0) && !ImGui.isMouseDragging(0, 2f);
    }

    private void drawLabels(float originX, float originY, int w, int h) {
        for (SpritePlacement placement : session.placements) {
            if (!"region".equals(placement.role)) {
                continue;
            }
            float sx = originX + (placement.centerX - (session.cameraX - (w / session.zoom) / 2f)) * session.zoom;
            float sy = originY + ((session.cameraY + (h / session.zoom) / 2f) - placement.centerY) * session.zoom;
            String text = placement.label;
            if (session.overlays.layerOrder) {
                text = text + "  z " + String.format("%.3f", placement.z);
            }
            if (session.overlays.names || session.overlays.layerOrder) {
                ImGui.getForegroundDrawList().addText(sx, sy, ImGui.colorConvertFloat4ToU32(0.9f, 0.92f, 0.95f, 1f), text);
            }
        }
    }

    private float worldX(float mx, int w) {
        float viewW = w / session.zoom;
        return session.cameraX - viewW / 2f + (mx / w) * viewW;
    }

    private float worldY(float my, int h) {
        float viewH = h / session.zoom;
        return session.cameraY + viewH / 2f - (my / h) * viewH;
    }

    private static boolean near(float x, float y, float px, float py, float radius) {
        float dx = x - px;
        float dy = y - py;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void pathModal() {
        if (prompt.isEmpty()) {
            return;
        }
        ImGui.openPopup("Path");
        if (ImGui.beginPopupModal("Path", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(promptTitle());
            ImGui.setNextItemWidth(420);
            ImGui.inputText("##path", pathBuffer);
            if (ImGui.button("Browse")) {
                String picked = NativeDialogs.pick(prompt, pathBuffer.get());
                if (picked != null) {
                    pathBuffer.set(picked);
                }
            }
            ImGui.sameLine();
            if (ImGui.button("OK") || ImGui.isKeyPressed(257)) {
                applyPath();
                prompt = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                prompt = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private String promptTitle() {
        return switch (prompt) {
            case "open" -> "Project folder";
            case "save" -> "Folder to create";
            case "import" -> "Folder that contains the PNGs";
            case "export" -> "JSON file path";
            default -> "Path";
        };
    }

    private void applyPath() {
        String text = pathBuffer.get().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            Path path = Path.of(text);
            switch (prompt) {
                case "open" -> {
                    session.open(path);
                    renderer.forgetTextures();
                }
                case "save" -> session.save(path);
                case "import" -> {
                    session.importFolder(path);
                    renderer.forgetTextures();
                }
                case "export" -> session.exportTo(path);
                default -> {
                }
            }
        } catch (Exception e) {
            session.log.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void askPath(String kind, String title) {
        prompt = kind;
        Path suggested = session.project.directory;
        if ("export".equals(kind)) {
            Path dir = suggested == null ? Path.of(System.getProperty("user.home")) : suggested.resolve("export");
            pathBuffer.set(dir.resolve(session.project.turret.name + ".json").toString());
        } else if (suggested != null) {
            pathBuffer.set(suggested.toString());
        } else {
            pathBuffer.set(Path.of(System.getProperty("user.home")).toString());
        }
        session.log.info(title);
    }

    private void save() {
        if (session.project.directory == null) {
            askPath("save", "Save project folder");
            return;
        }
        try {
            session.save(session.project.directory);
        } catch (Exception e) {
            session.log.error(e.getMessage());
        }
    }

    private void resetCamera() {
        session.cameraX = 0f;
        session.cameraY = 0f;
        session.zoom = 18f;
    }

    private void resetLayout() {
        try {
            Files.deleteIfExists(iniPath);
            Files.deleteIfExists(iniBackup);
        } catch (Exception ignored) {
            // The next dock build still runs.
        }
        session.viewportMaximized = false;
        session.resetLayout = true;
    }

    private void toggleMaximize() {
        if (!session.viewportMaximized) {
            try {
                ImGui.saveIniSettingsToDisk(iniBackup.toString());
            } catch (Exception ignored) {
                // Restore will fall back to the default dock.
            }
            session.viewportMaximized = true;
        } else {
            session.viewportMaximized = false;
            if (Files.isRegularFile(iniBackup)) {
                ImGui.loadIniSettingsFromDisk(iniBackup.toString());
            }
        }
    }

    private void about() {
        ImGui.setNextWindowSize(460, 280, ImGuiCond.Once);
        if (ImGui.begin("About EM3", ImGuiWindowFlags.NoCollapse)) {
            ImGui.text("EM3 v0.1.0");
            ImGui.textWrapped("Epic Mindustry Mod Maker. This preview evaluates Mindustry v160.7 RegionPart math "
                    + "in EM3's own OpenGL context. It does not call DrawTurret.draw.");
            ImGui.textWrapped("Sine progress uses Math.sin. Arc uses a lookup table. Sprite corners use Math.cos "
                    + "instead of Mathf.cosDeg. Nearest filtering skips the linear-filter bleed pass.");
            if (ImGui.button("Close")) {
                showAbout = false;
            }
        }
        ImGui.end();
    }

    private void syncNameBuffer() {
        RegionPartModel part = session.selectedPart();
        nameBuffer.set(part == null ? "" : part.label);
        if (part != null && part.progress != null) {
            progressBuffer.set(part.progress.isTextual() ? part.progress.asText() : part.progress.toString());
        }
    }

    private void applyTheme() {
        ImGui.styleColorsDark();
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(2f);
        style.setChildRounding(2f);
        style.setFrameRounding(1f);
        style.setPopupRounding(2f);
        style.setScrollbarRounding(2f);
        style.setGrabRounding(1f);
        style.setTabRounding(1f);
        style.setWindowBorderSize(1f);
        style.setFrameBorderSize(0f);
        style.setWindowPadding(8f, 6f);
        style.setFramePadding(6f, 3f);
        style.setItemSpacing(8f, 4f);
        style.setItemInnerSpacing(6f, 4f);
        color(style, ImGuiCol.WindowBg, 0.11f, 0.11f, 0.12f, 1f);
        color(style, ImGuiCol.ChildBg, 0.12f, 0.12f, 0.13f, 1f);
        color(style, ImGuiCol.PopupBg, 0.12f, 0.12f, 0.13f, 1f);
        color(style, ImGuiCol.Border, 0.22f, 0.22f, 0.24f, 1f);
        color(style, ImGuiCol.FrameBg, 0.07f, 0.07f, 0.08f, 1f);
        color(style, ImGuiCol.FrameBgHovered, 0.16f, 0.18f, 0.20f, 1f);
        color(style, ImGuiCol.FrameBgActive, 0.18f, 0.22f, 0.28f, 1f);
        color(style, ImGuiCol.TitleBg, 0.08f, 0.08f, 0.09f, 1f);
        color(style, ImGuiCol.TitleBgActive, 0.13f, 0.15f, 0.18f, 1f);
        color(style, ImGuiCol.MenuBarBg, 0.09f, 0.09f, 0.10f, 1f);
        color(style, ImGuiCol.Header, 0.18f, 0.26f, 0.34f, 1f);
        color(style, ImGuiCol.HeaderHovered, 0.24f, 0.36f, 0.48f, 1f);
        color(style, ImGuiCol.HeaderActive, 0.16f, 0.40f, 0.62f, 1f);
        color(style, ImGuiCol.Button, 0.18f, 0.20f, 0.22f, 1f);
        color(style, ImGuiCol.ButtonHovered, 0.24f, 0.30f, 0.36f, 1f);
        color(style, ImGuiCol.ButtonActive, 0.16f, 0.38f, 0.58f, 1f);
        color(style, ImGuiCol.Tab, 0.13f, 0.13f, 0.15f, 1f);
        color(style, ImGuiCol.TabHovered, 0.24f, 0.34f, 0.44f, 1f);
        color(style, ImGuiCol.TabActive, 0.18f, 0.24f, 0.30f, 1f);
        color(style, ImGuiCol.DockingPreview, 0.22f, 0.48f, 0.78f, 0.55f);
        color(style, ImGuiCol.Separator, 0.24f, 0.24f, 0.27f, 1f);
        color(style, ImGuiCol.Text, 0.86f, 0.87f, 0.88f, 1f);
    }

    private static void color(ImGuiStyle style, int col, float r, float g, float b, float a) {
        style.setColor(col, r, g, b, a);
    }
}
