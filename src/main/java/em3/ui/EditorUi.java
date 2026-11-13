package em3.ui;

import em3.animation.Keyframe;
import em3.animation.RegionMath;
import em3.animation.SpritePlacement;
import em3.animation.TrackMath;
import em3.app.EditorSession;
import em3.app.History;
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
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_G;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
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
    private float dragStartVecX;
    private float dragStartVecY;
    private float dragStartValue;
    private boolean dragUndoOpen;
    private String selectedProperty = "x";
    private int selectedKey = -1;
    private final List<Keyframe> copiedKeys = new ArrayList<>();
    private boolean escapeHeld;
    private boolean gHeld;
    private boolean rHeld;
    private boolean f5Held;
    private boolean ctrlZHeld;
    private boolean ctrlYHeld;
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
        loadUiFont(io);
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
                ImGui.setWindowFocus("animation");
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
        boolean escape = glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS;
        boolean keyG = glfwGetKey(window, GLFW_KEY_G) == GLFW_PRESS;
        boolean keyR = glfwGetKey(window, GLFW_KEY_R) == GLFW_PRESS;
        boolean keyF5 = glfwGetKey(window, GLFW_KEY_F5) == GLFW_PRESS;
        boolean ctrlZ = ctrl && !shift && glfwGetKey(window, GLFW_KEY_Z) == GLFW_PRESS;
        boolean redo = ctrl && (glfwGetKey(window, GLFW_KEY_Y) == GLFW_PRESS
                || (shift && glfwGetKey(window, GLFW_KEY_Z) == GLFW_PRESS));
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
        if (!ImGui.getIO().getWantTextInput()) {
            if (escape && !escapeHeld) {
                if (dragUndoOpen) {
                    session.history.cancel(session.project.turret);
                    dragUndoOpen = false;
                    dragAxis = 0;
                    session.refresh();
                } else if (session.test.active) {
                    session.test.active = false;
                    session.test.triggerHeld = false;
                }
            }
            if (keyG && !gHeld) {
                session.tool = "move";
            }
            if (keyR && !rHeld) {
                session.tool = "rotate";
            }
            if (keyF5 && !f5Held) {
                session.test.active = !session.test.active;
                session.test.triggerHeld = false;
            }
            if (ctrlZ && !ctrlZHeld && session.history.undo(session.project.turret)) {
                session.touch();
            }
            if (redo && !ctrlYHeld && session.history.redo(session.project.turret)) {
                session.touch();
            }
        }
        ctrlSHeld = ctrlS;
        ctrlOHeld = ctrlO;
        spaceHeld = space;
        keyFHeld = keyF;
        ctrlShiftMHeld = ctrlShiftM;
        escapeHeld = escape;
        gHeld = keyG;
        rHeld = keyR;
        f5Held = keyF5;
        ctrlZHeld = ctrlZ;
        ctrlYHeld = redo;
    }

    private void loadUiFont(ImGuiIO io) {
        String[] candidates = {
                "C:/Windows/Fonts/arial.ttf",
                "C:/Windows/Fonts/Arial.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Arial.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/arial.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };
        for (String path : candidates) {
            if (Files.isRegularFile(Path.of(path))) {
                io.getFonts().addFontFromFileTTF(path, 16f);
                session.log.info("ui font " + path);
                return;
            }
        }
        io.getFonts().addFontDefault();
        session.log.info("ui font fallback (arial was not found on this machine)");
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
        int dockId = ImGui.getID("EM3Dock2");
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
        imgui.internal.ImGui.dockBuilderDockWindow("hierarchy", left.get());
        imgui.internal.ImGui.dockBuilderDockWindow("inspector", right.get());
        imgui.internal.ImGui.dockBuilderDockWindow("scene", scene.get());
        imgui.internal.ImGui.dockBuilderDockWindow("json output", bottom.get());
        imgui.internal.ImGui.dockBuilderDockWindow("console", bottom.get());
        imgui.internal.ImGui.dockBuilderDockWindow("assets", bottom.get());
        imgui.internal.ImGui.dockBuilderDockWindow("animation", bottom.get());
        imgui.internal.ImGui.dockBuilderFinish(dockId);
    }

    private void menu() {
        if (!ImGui.beginMenuBar()) {
            return;
        }
        if (ImGui.beginMenu("file")) {
            if (ImGui.menuItem("new salvo project")) {
                session.newSalvo();
                renderer.forgetTextures();
            }
            if (ImGui.menuItem("open...", "Ctrl+O")) {
                askPath("open", "open project folder");
            }
            if (ImGui.menuItem("save", "Ctrl+S")) {
                save();
            }
            if (ImGui.menuItem("save as...")) {
                askPath("save", "save project folder");
            }
            if (ImGui.menuItem("export json...")) {
                askPath("export", "export json file");
            }
            ImGui.separator();
            if (ImGui.menuItem("exit")) {
                glfwSetWindowShouldClose(window, true);
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("edit")) {
            if (ImGui.menuItem("undo", "Ctrl+Z")) {
                if (session.history.undo(session.project.turret)) {
                    session.touch();
                }
            }
            if (ImGui.menuItem("redo", "Ctrl+Y")) {
                if (session.history.redo(session.project.turret)) {
                    session.touch();
                }
            }
            if (ImGui.menuItem("reset animation")) {
                session.playback.reset(session.params);
                session.params.rotation = 90f;
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("view")) {
            overlayItem("show outlines", "outlines");
            overlayItem("show part names", "names");
            overlayItem("show pivot points", "pivots");
            overlayItem("show bounding boxes", "bounds");
            overlayItem("show symmetry axis", "symmetry");
            overlayItem("show heat textures", "heat");
            overlayItem("show sprite transparency", "transparency");
            overlayItem("show part layer order", "layerOrder");
            overlayItem("show animation movement paths", "paths");
            overlayItem("show turret base", "base");
            overlayItem("show world grid", "grid");
            ImGui.separator();
            ImGui.text("ground shadow");
            if (ImGui.menuItem("show ground shadow", "", session.shadow.visible)) {
                session.shadow.visible = !session.shadow.visible;
            }
            if (ImGui.menuItem("isolate shadow", "", session.shadow.isolate)) {
                session.shadow.isolate = !session.shadow.isolate;
            }
            ImGui.separator();
            ImGui.text("outline preview");
            scratch[0] = session.overlays.outlineRadius;
            ImGui.setNextItemWidth(120);
            if (ImGui.dragFloat("Radius##outline", scratch, 0.1f, 1f, 12f, "%.0f")) {
                session.overlays.outlineRadius = Math.round(scratch[0]);
                renderer.forgetTextures();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("window")) {
            if (ImGui.menuItem("reset layout")) {
                resetLayout();
            }
            if (ImGui.menuItem(session.viewportMaximized ? "restore workspace" : "maximize viewport", "Ctrl+Shift+M")) {
                toggleMaximize();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("tools")) {
            if (ImGui.menuItem("download official salvo sprites")) {
                session.downloadOfficial();
                renderer.forgetTextures();
            }
            if (ImGui.menuItem("import sprites from folder...")) {
                askPath("import", "mindustry source or install folder");
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("help")) {
            if (ImGui.menuItem("about")) {
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
        ImGui.begin("hierarchy");
        TurretContent turret = session.project.turret;
        int flags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (ImGui.treeNodeEx(turret.name + "##root", flags)) {
            selectRow("base", "base");
            selectRow("main turret body", "preview");
            for (RegionPartModel part : turret.parts) {
                if (part.mirror) {
                    selectRow("right side", "part/" + part.id + "/side/0");
                    selectRow("left side", "part/" + part.id + "/side/1");
                } else {
                    selectRow(part.label, "part/" + part.id);
                }
                if (heatAvailable(part)) {
                    selectRow("heat overlay", "part/" + part.id + "/heat");
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
            RegionPartModel part = session.selectedPart();
            if (part != null && part.progress != null && part.progress.isTextual() && part.channel(part.progress.asText()) != null) {
                session.activeChannel = part.progress.asText();
                selectedKey = -1;
            }
        }
    }

    private void inspector() {
        ImGui.begin("inspector");
        if ("base".equals(session.selection)) {
            turretFields();
        } else if ("preview".equals(session.selection)) {
            ImGui.textWrapped("main turret body is the shadow sprite. DrawTurret draws salvo-preview tinted with Pal.shadow. "
                    + "There is no raw salvo.png, so the colored body region is not drawn.");
            ImGui.text("sprite");
            ImGui.sameLine();
            ImGui.text(session.project.turret.previewRegion);
        } else {
            RegionPartModel part = session.selectedPart();
            if (part == null) {
                ImGui.textWrapped("select a part in the hierarchy.");
            } else {
                partFields(part);
            }
        }
        SpritePlacement placement = session.selectedPlacement();
        if (placement != null) {
            ImGui.separator();
            ImGui.text("evaluated transform");
            ImGui.text(String.format("world  %.3f, %.3f", placement.centerX, placement.centerY));
            ImGui.text(String.format("draw rotation  %.2f", placement.rotation));
            ImGui.text(String.format("layer  %.4f", placement.z));
            ImGui.text("sprite  " + placement.sprite);
            if (placement.missing) {
                ImGui.textColored(1f, 0.55f, 0.35f, 1f, "sprite file is missing");
            }
        }
        ImGui.end();
    }

    private void turretFields() {
        TurretContent turret = session.project.turret;
        ImGui.text("turret");
        if (drag("size", turret.size, 0.1f, 1f, 8f)) {
            turret.size = Math.round(scratch[0]);
            session.touch();
        }
        if (drag("elevation", turret.elevation, 0.05f, -1f, 8f)) {
            turret.elevation = scratch[0];
            session.touch();
        }
        if (drag("block recoil", turret.blockRecoil, 0.05f, 0f, 8f)) {
            turret.blockRecoil = scratch[0];
            session.touch();
        }
        if (drag("recoil power", turret.recoilPow, 0.02f, 0.1f, 4f)) {
            turret.recoilPow = scratch[0];
            session.touch();
        }
        ImGui.textWrapped("block recoil is Turret.recoil. salvo sets it to 0. part recoil is a separate progress channel.");
    }

    private void partFields(RegionPartModel part) {
        if (!part.label.equals(nameBuffer.get())) {
            syncNameBuffer();
        }
        ImGui.text("label");
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##label", nameBuffer)) {
            part.label = nameBuffer.get().trim().isEmpty() ? part.label : nameBuffer.get().trim();
            session.touch();
        }
        ImGui.text("sprite suffix");
        ImGui.text(part.suffix == null || part.suffix.isEmpty() ? "(none)" : part.suffix);
        if (part.name != null && !part.name.isBlank()) {
            ImGui.text("sprite name  " + part.name);
        }
        transformField("x", "x", session.autoKey ? sampleActive(part, "x") : part.x);
        transformField("y", "y", session.autoKey ? sampleActive(part, "y") : part.y);
        if (drag("origin x", part.originX, 0.05f, -32f, 32f)) {
            part.originX = scratch[0];
            session.touch();
        }
        if (drag("origin y", part.originY, 0.05f, -32f, 32f)) {
            part.originY = scratch[0];
            session.touch();
        }
        transformField("rotation", "rotation", session.autoKey ? sampleActive(part, "rotation") : part.rotation);
        if (drag("scale x", part.xScl, 0.01f, -4f, 4f)) {
            part.xScl = scratch[0];
            session.touch();
        }
        if (drag("scale y", part.yScl, 0.01f, -4f, 4f)) {
            part.yScl = scratch[0];
            session.touch();
        }
        if (drag("move x", part.moveX, 0.02f, -8f, 8f)) {
            part.moveX = scratch[0];
            session.touch();
        }
        if (drag("move y", part.moveY, 0.02f, -8f, 8f)) {
            part.moveY = scratch[0];
            session.touch();
        }
        if (drag("move rotation", part.moveRot, 0.2f, -180f, 180f)) {
            part.moveRot = scratch[0];
            session.touch();
        }
        if (ImGui.checkbox("mirror", part.mirror)) {
            part.mirror = !part.mirror;
            session.touch();
        }
        progressField("progress", part);
        if (drag("layer", part.layer, 0.1f, -1f, 120f)) {
            part.layer = scratch[0];
            session.touch();
        }
        if (drag("layer offset", part.layerOffset, 0.0001f, -1f, 1f)) {
            part.layerOffset = scratch[0];
            session.touch();
        }
        if (ImGui.checkbox("outline", part.outline)) {
            part.outline = !part.outline;
            session.touch();
        }
        ImGui.sameLine();
        ImGui.textDisabled("exported");
        if (drag("outline layer offset", part.outlineLayerOffset, 0.0001f, -1f, 1f)) {
            part.outlineLayerOffset = scratch[0];
            session.touch();
        }
        ImGui.separator();
        ImGui.textDisabled("editor only");
        if (ImGui.checkbox("visible in editor", part.editorVisible)) {
            part.editorVisible = !part.editorVisible;
            session.touch();
        }
        ImGui.textWrapped("hiding a part here does not change the exported outline flag. view > show outlines is also preview-only.");
        ImGui.separator();
        ImGui.text("part moves");
        for (int i = 0; i < part.moves.size(); i++) {
            PartMoveModel move = part.moves.get(i);
            ImGui.pushID(i);
            ImGui.text(move.progress == null ? "progress" : move.progress.toString());
            if (drag("move x##m", move.x, 0.02f, -8f, 8f)) {
                move.x = scratch[0];
                session.touch();
            }
            if (drag("move y##m", move.y, 0.02f, -8f, 8f)) {
                move.y = scratch[0];
                session.touch();
            }
            if (drag("move rotation##m", move.rot, 0.2f, -180f, 180f)) {
                move.rot = scratch[0];
                session.touch();
            }
            if (ImGui.button("remove move")) {
                session.removeMove(i);
            }
            ImGui.popID();
        }
        if (ImGui.button("add recoil move")) {
            session.addMove();
        }
        if (part.extras != null && !part.extras.isEmpty()) {
            ImGui.separator();
            ImGui.textWrapped("preserved fields the inspector does not edit:");
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

    private void transformField(String label, String property, float value) {
        float speed = "rotation".equals(property) ? 0.2f : 0.02f;
        float limit = "rotation".equals(property) ? 180f : 16f;
        if (!drag(label, value, speed, -limit, limit)) {
            if (dragUndoOpen && ImGui.isItemDeactivatedAfterEdit()) {
                session.history.commit(session.project.turret);
                dragUndoOpen = false;
                session.refresh();
            }
            return;
        }
        if (ImGui.isItemActivated()) {
            session.history.begin(session.project.turret);
            dragUndoOpen = true;
        }
        RegionPartModel part = session.selectedPart();
        if (part == null) {
            return;
        }
        float next = scratch[0];
        if (session.autoKey) {
            float t = session.params.channel(session.activeChannel);
            part.upsertKey(session.activeChannel, property, Float.isNaN(t) ? 0f : t, next);
        } else if ("x".equals(property)) {
            part.x = next;
        } else if ("y".equals(property)) {
            part.y = next;
        } else {
            part.rotation = next;
        }
        session.project.dirty = true;
        if (ImGui.isItemDeactivatedAfterEdit()) {
            session.history.commit(session.project.turret);
            dragUndoOpen = false;
        }
        session.refresh();
    }

    private boolean drag(String label, float value, float speed, float min, float max) {
        scratch[0] = value;
        ImGui.setNextItemWidth(-1);
        String caption = label.contains("##") ? label.substring(0, label.indexOf('#')) : label;
        ImGui.text(caption);
        return ImGui.dragFloat("##" + label, scratch, speed, min, max, "%.3f");
    }

    private void animation() {
        ImGui.begin("animation");
        if (ImGui.button(session.playback.playing ? "pause" : "play")) {
            session.playback.playing = !session.playback.playing;
        }
        ImGui.sameLine();
        if (ImGui.button("reset")) {
            session.playback.reset(session.params);
            session.params.rotation = 90f;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("loop", session.playback.loop)) {
            session.playback.loop = !session.playback.loop;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("reverse", session.playback.reverse)) {
            session.playback.reverse = !session.playback.reverse;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("auto-key", session.autoKey)) {
            session.autoKey = !session.autoKey;
        }
        ImGui.sameLine();
        ImGui.textDisabled(session.autoKey ? "editing writes a keyframe" : "editing changes the base transform");
        scratch[0] = session.playback.cycle;
        ImGui.setNextItemWidth(180);
        if (ImGui.sliderFloat("preview##cycle", scratch, 0f, 1f, "%.3f")) {
            session.playback.scrub(scratch[0], session.params);
        }
        ImGui.sameLine();
        if (drag("speed", session.playback.speed, 0.02f, 0.05f, 4f)) {
            session.playback.speed = scratch[0];
        }
        boolean firstChannel = true;
        for (String name : new String[]{"warmup", "recoil", "charge", "reload"}) {
            if (!firstChannel) {
                ImGui.sameLine();
            }
            firstChannel = false;
            if (name.equals(session.activeChannel)) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.45f, 0.32f, 0.18f, 1f);
            }
            if (ImGui.button(name)) {
                session.activeChannel = name;
                selectedKey = -1;
            }
            if (name.equals(session.activeChannel)) {
                ImGui.popStyleColor();
            }
        }
        channel(session.activeChannel, session.activeChannel);
        timeline();
        if (ImGui.checkbox("spin with playback", session.playback.rotateWithPlayback)) {
            session.playback.rotateWithPlayback = !session.playback.rotateWithPlayback;
        }
        ImGui.sameLine();
        channel("heat", "heat");
        ImGui.textDisabled("reload uses draw turret's scale: 1 is just fired. heat is a visual overlay, not a movement channel.");
        ImGui.end();
    }

    private void timeline() {
        RegionPartModel part = session.selectedPart();
        if (part == null) {
            ImGui.textDisabled("select a part to edit its tracks");
            return;
        }
        float time = session.params.channel(session.activeChannel);
        if (Float.isNaN(time)) {
            time = 0f;
        }
        ImGui.text(part.label + "  " + session.activeChannel);
        ImGui.sameLine();
        if (ImGui.button("add key")) {
            float t = time;
            session.edit(() -> {
                for (String property : new String[]{"x", "y", "rotation"}) {
                    float value = part.hasTrack(session.activeChannel, property)
                            ? part.sampleTrack(session.activeChannel, property, t) : 0f;
                    if (Float.isNaN(value)) {
                        value = 0f;
                    }
                    part.upsertKey(session.activeChannel, property, t, value);
                }
            });
        }
        ImGui.sameLine();
        if (ImGui.button("delete key") && selectedKey >= 0) {
            session.edit(() -> deleteSelectedKey(part));
        }
        ImGui.sameLine();
        if (ImGui.button("copy")) {
            copySelected(part);
        }
        ImGui.sameLine();
        if (ImGui.button("paste")) {
            session.edit(() -> pasteKeys(part));
        }
        var channel = part.channel(session.activeChannel);
        String[] properties = {"x", "y", "rotation", "xScl", "yScl"};
        float width = Math.max(120f, ImGui.getContentRegionAvailX() - 90f);
        for (String property : properties) {
            ImGui.text(property);
            ImGui.sameLine(80);
            ImVec2 origin = ImGui.getCursorScreenPos();
            ImGui.invisibleButton("track-" + property, width, 18);
            var draw = ImGui.getWindowDrawList();
            float pad = 8f;
            float span = Math.max(1f, width - pad * 2f);
            draw.addRectFilled(origin.x + pad, origin.y + 8, origin.x + pad + span, origin.y + 10,
                    ImGui.colorConvertFloat4ToU32(0.35f, 0.32f, 0.28f, 1f));
            float playX = origin.x + pad + TrackMath.clamp01(time) * span;
            draw.addLine(playX, origin.y, playX, origin.y + 18, ImGui.colorConvertFloat4ToU32(0.95f, 0.86f, 0.55f, 1f), 1.5f);
            var track = channel == null ? null : channel.track(property);
            if (track != null) {
                for (int i = 0; i < track.keys.size(); i++) {
                    Keyframe key = track.keys.get(i);
                    float kx = origin.x + pad + TrackMath.clamp01(key.time) * span;
                    int color = (property.equals(selectedProperty) && i == selectedKey)
                            ? ImGui.colorConvertFloat4ToU32(0.95f, 0.72f, 0.28f, 1f)
                            : ImGui.colorConvertFloat4ToU32(0.90f, 0.90f, 0.86f, 1f);
                    draw.addQuadFilled(kx, origin.y + 9, kx + 5, origin.y + 4, kx, origin.y - 1, kx - 5, origin.y + 4, color);
                    if (ImGui.isItemHovered() && ImGui.isMouseClicked(0)
                            && Math.abs(ImGui.getIO().getMousePosX() - kx) < 7f) {
                        selectedProperty = property;
                        selectedKey = i;
                    }
                    if (property.equals(selectedProperty) && i == selectedKey && ImGui.isItemActive() && ImGui.isMouseDragging(0)) {
                        if (!dragUndoOpen) {
                            session.history.begin(session.project.turret);
                            dragUndoOpen = true;
                        }
                        float next = TrackMath.clamp01((ImGui.getIO().getMousePosX() - origin.x - pad) / span);
                        key.time = next;
                        session.project.dirty = true;
                    }
                }
            }
            if (dragUndoOpen && ImGui.isMouseReleased(0)) {
                session.history.commit(session.project.turret);
                dragUndoOpen = false;
                session.refresh();
            }
        }
        editSelectedKey(part);
    }

    private void editSelectedKey(RegionPartModel part) {
        var channel = part.channel(session.activeChannel);
        var track = channel == null ? null : channel.track(selectedProperty);
        if (track == null || selectedKey < 0 || selectedKey >= track.keys.size()) {
            return;
        }
        Keyframe key = track.keys.get(selectedKey);
        scratch[0] = key.time;
        ImGui.setNextItemWidth(120);
        if (ImGui.dragFloat("time##key", scratch, 0.002f, 0f, 1f, "%.3f")) {
            float next = scratch[0];
            session.edit(() -> key.time = TrackMath.clamp01(next));
        }
        ImGui.sameLine();
        scratch[0] = key.value;
        ImGui.setNextItemWidth(140);
        if (ImGui.dragFloat("value##key", scratch, 0.02f, -64f, 64f, "%.3f")) {
            float next = scratch[0];
            session.edit(() -> key.value = next);
        }
        ImGui.sameLine();
        boolean step = "step".equals(key.interpolation);
        if (ImGui.checkbox("step", step)) {
            session.edit(() -> key.interpolation = step ? "linear" : "step");
        }
        if (step) {
            ImGui.sameLine();
            ImGui.textColored(0.95f, 0.72f, 0.35f, 1f, "step is preview only and is not exported");
        }
    }

    private void deleteSelectedKey(RegionPartModel part) {
        var channel = part.channel(session.activeChannel);
        var track = channel == null ? null : channel.track(selectedProperty);
        if (track != null && selectedKey >= 0 && selectedKey < track.keys.size()) {
            track.keys.remove(selectedKey);
            selectedKey = -1;
        }
    }

    private void copySelected(RegionPartModel part) {
        copiedKeys.clear();
        var channel = part.channel(session.activeChannel);
        var track = channel == null ? null : channel.track(selectedProperty);
        if (track == null) {
            return;
        }
        if (selectedKey >= 0 && selectedKey < track.keys.size()) {
            copiedKeys.add(track.keys.get(selectedKey).copy());
        } else {
            for (Keyframe key : track.keys) {
                copiedKeys.add(key.copy());
            }
        }
    }

    private void pasteKeys(RegionPartModel part) {
        if (copiedKeys.isEmpty()) {
            return;
        }
        var track = part.channelOrCreate(session.activeChannel).trackOrCreate(selectedProperty);
        for (Keyframe key : copiedKeys) {
            track.keys.add(key.copy());
        }
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
        ImGui.begin("json output");
        if (ImGui.button("copy")) {
            ImGui.setClipboardText(session.exportText);
            session.log.info("copied json to the clipboard");
        }
        ImGui.sameLine();
        if (ImGui.button("save output")) {
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
        ImGui.begin("console");
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
        ImGui.begin("assets");
        ImGui.text("mindustry " + session.project.mindustryVersion);
        if (session.project.directory != null) {
            ImGui.textWrapped(session.project.directory.toString());
        } else {
            ImGui.textDisabled("project has not been saved yet");
        }
        if (ImGui.button("download v160.7 salvo sprites")) {
            session.downloadOfficial();
            renderer.forgetTextures();
        }
        ImGui.sameLine();
        if (ImGui.button("import folder")) {
            askPath("import", "Mindustry source or install folder");
        }
        if (session.project.sprites.isEmpty()) {
            ImGui.textColored(1f, 0.62f, 0.35f, 1f, "no sprites are linked.");
            ImGui.textWrapped("use download, or import a checkout of Anuken/Mindustry at tag "
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
        ImGui.begin("scene", flags);
        boolean missing = session.project.sprites.isEmpty() || !session.sprites.failures().isEmpty();
        if (missing) {
            ImGui.textColored(1f, 0.62f, 0.35f, 1f, "salvo sprites are not loaded.");
            ImGui.textWrapped("tools > download official salvo sprites, or import a mindustry " + MindustryV160.VERSION_TAG
                    + " source tree. the preview stays empty until those pngs are available.");
        }
        if (ImGui.button("reset camera")) {
            resetCamera();
        }
        ImGui.sameLine();
        if (ImGui.button(session.test.active ? "exit test" : "test")) {
            session.test.active = !session.test.active;
            session.test.triggerHeld = false;
        }
        ImGui.sameLine();
        if (ImGui.button(session.tool.equals("rotate") ? "rotate (r)" : "move (g)")) {
            session.tool = session.tool.equals("rotate") ? "move" : "rotate";
        }
        ImGui.sameLine();
        ImGui.textDisabled("wheel zoom, middle-mouse pan, f reset, f5 test");
        if (session.test.active) {
            testSettings();
        }
        if (ImGui.collapsingHeader("shadow")) {
            shadowSettings();
        }
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
            if (ImGui.isMouseDragging(2) && dragAxis == 0) {
                session.cameraX -= ImGui.getIO().getMouseDeltaX() / session.zoom;
                session.cameraY += ImGui.getIO().getMouseDeltaY() / session.zoom;
            }
        }
        boolean sceneFocused = hovered || ImGui.isWindowFocused();
        if (session.test.active) {
            session.test.triggerHeld = false;
            if (hovered) {
                float wx = worldX(mx, w);
                float wy = worldY(my, h);
                session.test.aimDegrees = (float) Math.toDegrees(Math.atan2(wy - session.params.y, wx - session.params.x));
                session.test.triggerHeld = ImGui.isMouseDown(0);
                if (ImGui.isMouseClicked(0) && !session.test.repeat) {
                    session.test.fire(session.params);
                }
            }
        } else if (hovered || dragAxis != 0) {
            handleSelection(mx, my, w, h, sceneFocused);
        }
        if (session.overlays.names || session.overlays.layerOrder) {
            drawLabels(origin.x, origin.y, w, h);
        }
        ImGui.end();
    }

    private void handleSelection(float mx, float my, int w, int h, boolean sceneFocused) {
        float wx = worldX(mx, w);
        float wy = worldY(my, h);
        SpritePlacement selected = session.selectedPlacement();
        if (selected != null && "region".equals(selected.role) && ImGui.isMouseDown(0) && sceneFocused) {
            float sign = selected.side == 1 ? -1f : 1f;
            float[] axis = RegionMath.localAxis(session.params.rotation, sign);
            float len = 2.2f;
            if (dragAxis == 0 && ImGui.isMouseClicked(0)) {
                if ("rotate".equals(session.tool) && nearRing(wx, wy, selected.centerX, selected.centerY, len, 0.55f)) {
                    beginDrag(3, wx, wy, selected, sign);
                } else if (!"rotate".equals(session.tool)
                        && near(wx, wy, selected.centerX + axis[0] * len, selected.centerY + axis[1] * len, 0.55f)) {
                    beginDrag(1, wx, wy, selected, sign);
                } else if (!"rotate".equals(session.tool)
                        && near(wx, wy, selected.centerX + axis[2] * len, selected.centerY + axis[3] * len, 0.55f)) {
                    beginDrag(2, wx, wy, selected, sign);
                }
            }
            if (dragAxis != 0) {
                applyDrag(wx, wy, sign);
                return;
            }
        } else if (dragAxis != 0) {
            session.history.commit(session.project.turret);
            dragUndoOpen = false;
            dragAxis = 0;
            session.refresh();
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

    private void beginDrag(int axis, float wx, float wy, SpritePlacement selected, float sign) {
        dragAxis = axis;
        dragOriginX = wx;
        dragOriginY = wy;
        dragStartVecX = wx - selected.centerX;
        dragStartVecY = wy - selected.centerY;
        RegionPartModel part = session.selectedPart();
        if (part == null) {
            dragStartValue = 0f;
            return;
        }
        session.history.begin(session.project.turret);
        dragUndoOpen = true;
        if (axis == 3) {
            dragStartValue = session.autoKey ? sampleActive(part, "rotation") : part.rotation;
        } else if (axis == 1) {
            dragStartValue = session.autoKey ? sampleActive(part, "x") : part.x;
        } else {
            dragStartValue = session.autoKey ? sampleActive(part, "y") : part.y;
        }
    }

    private void applyDrag(float wx, float wy, float sign) {
        RegionPartModel part = session.selectedPart();
        if (part == null) {
            return;
        }
        SpritePlacement selected = session.selectedPlacement();
        float dx = wx - dragOriginX;
        float dy = wy - dragOriginY;
        float value;
        String property;
        if (dragAxis == 3) {
            float visual = RegionMath.rotationDelta(dragStartVecX, dragStartVecY,
                    wx - selected.centerX, wy - selected.centerY);
            value = dragStartValue + visual * sign;
            if (session.snapEnabled) {
                value = RegionMath.snap(value, session.rotationSnap);
            }
            property = "rotation";
        } else if (dragAxis == 1) {
            value = dragStartValue + RegionMath.moveXDelta(dx, dy, session.params.rotation, sign);
            if (session.snapEnabled) {
                value = RegionMath.snap(value, session.positionSnap);
            }
            property = "x";
        } else {
            value = dragStartValue + RegionMath.moveYDelta(dx, dy, session.params.rotation, sign);
            if (session.snapEnabled) {
                value = RegionMath.snap(value, session.positionSnap);
            }
            property = "y";
        }
        if (session.autoKey) {
            float t = session.params.channel(session.activeChannel);
            part.upsertKey(session.activeChannel, property, Float.isNaN(t) ? 0f : t, value);
        } else if ("x".equals(property)) {
            part.x = value;
        } else if ("y".equals(property)) {
            part.y = value;
        } else {
            part.rotation = value;
        }
        session.project.dirty = true;
    }

    private float sampleActive(RegionPartModel part, String property) {
        float t = session.params.channel(session.activeChannel);
        if (Float.isNaN(t) || !part.hasTrack(session.activeChannel, property)) {
            return 0f;
        }
        float sampled = part.sampleTrack(session.activeChannel, property, t);
        return Float.isNaN(sampled) ? 0f : sampled;
    }

    private static boolean nearRing(float x, float y, float cx, float cy, float radius, float slack) {
        float d = (float) Math.hypot(x - cx, y - cy);
        return Math.abs(d - radius) <= slack;
    }

    private void testSettings() {
        ImGui.text("test  " + session.test.phase + (session.test.frozen ? "  frozen" : ""));
        if (ImGui.button(session.test.paused ? "resume" : "pause")) {
            session.test.paused = !session.test.paused;
        }
        ImGui.sameLine();
        if (ImGui.button(session.test.frozen ? "unfreeze" : "freeze")) {
            session.test.frozen = !session.test.frozen;
        }
        ImGui.sameLine();
        if (ImGui.button("reset test")) {
            session.test.reset(session.params);
        }
        scratch[0] = session.test.fireRate;
        ImGui.setNextItemWidth(140);
        if (ImGui.sliderFloat("fire rate##test", scratch, 0.5f, 15f, "%.2f shots/s")) {
            session.test.fireRate = scratch[0];
        }
        ImGui.sameLine();
        scratch[0] = session.test.rotationSpeed;
        ImGui.setNextItemWidth(140);
        if (ImGui.sliderFloat("turn##test", scratch, 10f, 720f, "%.0f deg/s")) {
            session.test.rotationSpeed = scratch[0];
        }
        if (ImGui.checkbox("warmup", session.test.autoWarmup)) {
            session.test.autoWarmup = !session.test.autoWarmup;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("cooldown", session.test.autoCooldown)) {
            session.test.autoCooldown = !session.test.autoCooldown;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("repeat", session.test.repeat)) {
            session.test.repeat = !session.test.repeat;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("loop", session.test.loop)) {
            session.test.loop = !session.test.loop;
        }
        ImGui.sameLine();
        scratch[0] = session.test.timeScale;
        ImGui.setNextItemWidth(100);
        if (ImGui.sliderFloat("time##test", scratch, 0.1f, 3f, "%.2f")) {
            session.test.timeScale = scratch[0];
        }
    }

    private void shadowSettings() {
        if (ImGui.checkbox("shadow", session.shadow.visible)) {
            session.shadow.visible = !session.shadow.visible;
        }
        ImGui.sameLine();
        scratch[0] = session.shadow.opacity;
        ImGui.setNextItemWidth(90);
        if (ImGui.sliderFloat("opacity##shadow", scratch, 0f, 1f, "%.2f")) {
            session.shadow.opacity = scratch[0];
        }
        ImGui.sameLine();
        float elevation = MindustryV160.elevationFor(session.project.turret.size, session.project.turret.elevation);
        scratch[0] = session.shadow.offsetX(elevation);
        ImGui.setNextItemWidth(80);
        if (ImGui.dragFloat("shadow x", scratch, 0.05f, -8f, 8f, "%.2f")) {
            session.shadow.customOffsetX = scratch[0];
        }
        ImGui.sameLine();
        scratch[0] = session.shadow.offsetY(elevation);
        ImGui.setNextItemWidth(80);
        if (ImGui.dragFloat("shadow y", scratch, 0.05f, -8f, 8f, "%.2f")) {
            session.shadow.customOffsetY = scratch[0];
        }
        ImGui.sameLine();
        if (ImGui.checkbox("isolate", session.shadow.isolate)) {
            session.shadow.isolate = !session.shadow.isolate;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("snap", session.snapEnabled)) {
            session.snapEnabled = !session.snapEnabled;
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
            if (ImGui.button("browse")) {
                String picked = NativeDialogs.pick(prompt, pathBuffer.get());
                if (picked != null) {
                    pathBuffer.set(picked);
                }
            }
            ImGui.sameLine();
            if (ImGui.button("ok") || ImGui.isKeyPressed(257)) {
                applyPath();
                prompt = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("cancel")) {
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
        if (ImGui.begin("epic mindustry mod maker (em3)", ImGuiWindowFlags.NoCollapse)) {
            ImGui.text("epic mindustry mod maker (em3)");
            ImGui.text("v0.2.0");
            ImGui.textWrapped("this preview evaluates mindustry v160.7 region part math "
                    + "in em3's own opengl context. it does not call DrawTurret.draw.");
            ImGui.textWrapped("sine progress uses Math.sin. arc uses a lookup table. sprite corners use Math.cos "
                    + "instead of Mathf.cosDeg. Nearest filtering skips the linear-filter bleed pass.");
            if (ImGui.button("close")) {
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
        style.setFrameRounding(2f);
        style.setPopupRounding(2f);
        style.setScrollbarRounding(2f);
        style.setGrabRounding(2f);
        style.setTabRounding(2f);
        style.setWindowBorderSize(1f);
        style.setFrameBorderSize(0f);
        style.setWindowPadding(8f, 6f);
        style.setFramePadding(6f, 3f);
        style.setItemSpacing(6f, 4f);
        style.setItemInnerSpacing(4f, 3f);
        color(style, ImGuiCol.WindowBg, 0.16f, 0.14f, 0.13f, 1f);
        color(style, ImGuiCol.ChildBg, 0.17f, 0.15f, 0.14f, 1f);
        color(style, ImGuiCol.PopupBg, 0.17f, 0.15f, 0.14f, 1f);
        color(style, ImGuiCol.Border, 0.28f, 0.25f, 0.22f, 1f);
        color(style, ImGuiCol.FrameBg, 0.11f, 0.10f, 0.09f, 1f);
        color(style, ImGuiCol.FrameBgHovered, 0.22f, 0.19f, 0.16f, 1f);
        color(style, ImGuiCol.FrameBgActive, 0.28f, 0.22f, 0.16f, 1f);
        color(style, ImGuiCol.TitleBg, 0.12f, 0.11f, 0.10f, 1f);
        color(style, ImGuiCol.TitleBgActive, 0.20f, 0.16f, 0.13f, 1f);
        color(style, ImGuiCol.MenuBarBg, 0.13f, 0.12f, 0.11f, 1f);
        color(style, ImGuiCol.Header, 0.28f, 0.22f, 0.16f, 1f);
        color(style, ImGuiCol.HeaderHovered, 0.36f, 0.28f, 0.18f, 1f);
        color(style, ImGuiCol.HeaderActive, 0.46f, 0.32f, 0.16f, 1f);
        color(style, ImGuiCol.Button, 0.24f, 0.20f, 0.17f, 1f);
        color(style, ImGuiCol.ButtonHovered, 0.34f, 0.27f, 0.20f, 1f);
        color(style, ImGuiCol.ButtonActive, 0.46f, 0.32f, 0.16f, 1f);
        color(style, ImGuiCol.Tab, 0.16f, 0.14f, 0.13f, 1f);
        color(style, ImGuiCol.TabHovered, 0.34f, 0.26f, 0.18f, 1f);
        color(style, ImGuiCol.TabActive, 0.24f, 0.19f, 0.15f, 1f);
        color(style, ImGuiCol.DockingPreview, 0.55f, 0.38f, 0.18f, 0.50f);
        color(style, ImGuiCol.Separator, 0.30f, 0.26f, 0.22f, 1f);
        color(style, ImGuiCol.Text, 0.93f, 0.91f, 0.86f, 1f);
    }

    private static void color(ImGuiStyle style, int col, float r, float g, float b, float a) {
        style.setColor(col, r, g, b, a);
    }
}
