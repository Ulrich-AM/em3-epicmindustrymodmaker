package em3.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import em3.animation.AnimTrack;
import em3.animation.ChannelAnimation;
import em3.animation.Keyframe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Reads and writes the EM3 project directory. */
public final class ProjectStore {
    public static final String FILE_NAME = "project.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ProjectStore() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static void save(Em3Project project, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path assets = directory.resolve("assets");
        Files.createDirectories(assets);
        Files.createDirectories(directory.resolve("turrets"));
        Files.createDirectories(directory.resolve("sprites"));
        Files.createDirectories(directory.resolve("effects"));
        Files.createDirectories(directory.resolve("units"));
        Files.createDirectories(directory.resolve("factories"));
        Files.createDirectories(directory.resolve("export"));

        Map<String, String> rewritten = new java.util.LinkedHashMap<>();
        for (var entry : project.sprites.entrySet()) {
            Path source = project.directory == null
                    ? Path.of(entry.getValue())
                    : project.resolveSprite(entry.getKey());
            if (source == null) {
                source = Path.of(entry.getValue());
            }
            if (!source.isAbsolute() && project.directory != null) {
                source = project.directory.resolve(source);
            }
            String fileName = entry.getKey() + ".png";
            Path dest = assets.resolve(fileName);
            if (Files.isRegularFile(source)) {
                if (!source.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                    Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                rewritten.put(entry.getKey(), "assets/" + fileName);
            } else {
                rewritten.put(entry.getKey(), entry.getValue());
            }
        }
        project.sprites.clear();
        project.sprites.putAll(rewritten);
        project.directory = directory;
        ObjectNode root = MAPPER.createObjectNode();
        root.put("format", project.format);
        root.put("name", project.name);
        root.put("mindustryVersion", project.mindustryVersion);
        root.set("turret", turretToJson(project.turret));
        ObjectNode sprites = root.putObject("sprites");
        for (var entry : project.sprites.entrySet()) {
            sprites.put(entry.getKey(), entry.getValue());
        }
        MAPPER.writeValue(directory.resolve(FILE_NAME).toFile(), root);
        project.dirty = false;
    }

    public static Em3Project open(Path directory) throws IOException {
        Path file = Files.isDirectory(directory) ? directory.resolve(FILE_NAME) : directory;
        Path dir = Files.isDirectory(directory) ? directory : directory.getParent();
        JsonNode root = MAPPER.readTree(file.toFile());
        Em3Project project = new Em3Project();
        project.directory = dir;
        project.format = root.path("format").asInt(1);
        project.name = root.path("name").asText("Untitled");
        project.mindustryVersion = root.path("mindustryVersion").asText("");
        if (root.has("turret")) {
            project.turret = turretFromJson(root.get("turret"));
        }
        JsonNode sprites = root.get("sprites");
        if (sprites != null && sprites.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = sprites.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                project.sprites.put(field.getKey(), field.getValue().asText());
            }
        }
        project.dirty = false;
        return project;
    }

    public static ObjectNode turretToJson(TurretContent turret) {
        ObjectNode node = turret.extras == null ? MAPPER.createObjectNode() : turret.extras.deepCopy();
        node.remove("drawer");
        node.put("type", turret.type);
        node.put("name", turret.name);
        node.put("size", turret.size);
        node.put("recoil", num(turret.blockRecoil));
        node.put("recoilPow", num(turret.recoilPow));
        node.put("elevation", num(turret.elevation));
        node.put("baseRegion", turret.baseRegion);
        node.put("previewRegion", turret.previewRegion);
        node.put("bodyRegion", turret.bodyRegion);
        var parts = node.putArray("parts");
        for (RegionPartModel part : turret.parts) {
            parts.add(partToJson(part));
        }
        return node;
    }

    public static TurretContent turretFromJson(JsonNode node) {
        TurretContent turret = new TurretContent();
        turret.type = node.path("type").asText("ItemTurret");
        turret.name = node.path("name").asText("turret");
        turret.size = node.path("size").asInt(2);
        turret.blockRecoil = (float) node.path("recoil").asDouble(0);
        turret.recoilPow = (float) node.path("recoilPow").asDouble(1.8);
        turret.elevation = (float) node.path("elevation").asDouble(1);
        turret.baseRegion = node.path("baseRegion").asText("block-" + turret.size);
        turret.previewRegion = node.path("previewRegion").asText(turret.name + "-preview");
        turret.bodyRegion = node.path("bodyRegion").asText(turret.name);
        JsonNode parts = node.get("parts");
        if (parts != null && parts.isArray()) {
            for (JsonNode part : parts) {
                turret.parts.add(partFromJson(part));
            }
        }
        if (node instanceof ObjectNode object) {
            ObjectNode extras = object.deepCopy();
            for (String key : List.of("type", "name", "size", "recoil", "recoilPow", "elevation",
                    "baseRegion", "previewRegion", "bodyRegion", "parts", "drawer")) {
                extras.remove(key);
            }
            if (!extras.isEmpty()) {
                turret.extras = extras;
            }
        }
        return turret;
    }

    public static ObjectNode partToJson(RegionPartModel part) {
        ObjectNode node = part.extras == null ? MAPPER.createObjectNode() : part.extras.deepCopy();
        node.remove(List.of("editorVisible", "id", "label", "turretShading"));
        node.put("type", "RegionPart");
        node.put("suffix", part.suffix);
        if (part.name != null && !part.name.isBlank()) {
            node.put("name", part.name);
        }
        node.put("mirror", part.mirror);
        node.put("outline", part.outline);
        node.put("drawRegion", part.drawRegion);
        node.put("under", part.under);
        node.put("clampProgress", part.clampProgress);
        putNum(node, "x", part.x);
        putNum(node, "y", part.y);
        putNum(node, "originX", part.originX);
        putNum(node, "originY", part.originY);
        putNum(node, "rotation", part.rotation);
        putNum(node, "xScl", part.xScl);
        putNum(node, "yScl", part.yScl);
        putNum(node, "moveX", part.moveX);
        putNum(node, "moveY", part.moveY);
        putNum(node, "moveRot", part.moveRot);
        putNum(node, "growX", part.growX);
        putNum(node, "growY", part.growY);
        putNum(node, "layer", part.layer);
        putNum(node, "layerOffset", part.layerOffset);
        putNum(node, "outlineLayerOffset", part.outlineLayerOffset);
        putNum(node, "heatLayerOffset", part.heatLayerOffset);
        if (part.progress != null) {
            node.set("progress", part.progress);
        }
        if (part.heatProgress != null) {
            node.set("heatProgress", part.heatProgress);
        }
        if (part.growProgress != null) {
            node.set("growProgress", part.growProgress);
        }
        var moves = node.putArray("moves");
        for (PartMoveModel move : part.moves) {
            moves.add(moveToJson(move));
        }
        if (!part.children.isEmpty()) {
            var children = node.putArray("children");
            for (RegionPartModel child : part.children) {
                children.add(partToJson(child));
            }
        }
        if (!part.animations.isEmpty()) {
            var animations = node.putArray("animations");
            for (ChannelAnimation channel : part.animations) {
                animations.add(animationToJson(channel));
            }
        }
        node.put("id", part.id);
        node.put("label", part.label);
        node.put("editorVisible", part.editorVisible);
        return node;
    }

    public static RegionPartModel partFromJson(JsonNode node) {
        RegionPartModel part = new RegionPartModel();
        part.id = node.path("id").asText("part");
        part.label = node.path("label").asText(part.suffix.isEmpty() ? "Part" : part.id);
        part.suffix = node.path("suffix").asText("");
        if (node.hasNonNull("name")) {
            part.name = node.get("name").asText();
        }
        part.mirror = node.path("mirror").asBoolean(false);
        part.outline = node.path("outline").asBoolean(true);
        part.drawRegion = node.path("drawRegion").asBoolean(true);
        part.under = node.path("under").asBoolean(false);
        part.editorVisible = node.path("editorVisible").asBoolean(true);
        part.clampProgress = node.path("clampProgress").asBoolean(true);
        part.turretShading = node.path("turretShading").asBoolean(true);
        part.x = f(node, "x");
        part.y = f(node, "y");
        part.originX = f(node, "originX");
        part.originY = f(node, "originY");
        part.rotation = f(node, "rotation");
        part.xScl = node.has("xScl") ? f(node, "xScl") : 1f;
        part.yScl = node.has("yScl") ? f(node, "yScl") : 1f;
        part.moveX = f(node, "moveX");
        part.moveY = f(node, "moveY");
        part.moveRot = f(node, "moveRot");
        part.growX = f(node, "growX");
        part.growY = f(node, "growY");
        part.layer = node.has("layer") ? f(node, "layer") : -1f;
        part.layerOffset = f(node, "layerOffset");
        part.outlineLayerOffset = node.has("outlineLayerOffset") ? f(node, "outlineLayerOffset") : -0.001f;
        part.heatLayerOffset = node.has("heatLayerOffset") ? f(node, "heatLayerOffset") : 1f;
        part.progress = node.get("progress");
        part.heatProgress = node.get("heatProgress");
        part.growProgress = node.get("growProgress");
        if (node.has("label")) {
            part.label = node.get("label").asText();
        } else if (part.suffix.startsWith("-")) {
            part.label = part.suffix.substring(1);
        }
        if (node.has("moves") && node.get("moves").isArray()) {
            for (JsonNode move : node.get("moves")) {
                part.moves.add(moveFromJson(move));
            }
        }
        if (node.has("children") && node.get("children").isArray()) {
            for (JsonNode child : node.get("children")) {
                part.children.add(partFromJson(child));
            }
        }
        if (node.has("animations") && node.get("animations").isArray()) {
            for (JsonNode channel : node.get("animations")) {
                part.animations.add(animationFromJson(channel));
            }
        }
        if (node instanceof ObjectNode object) {
            ObjectNode extras = object.deepCopy();
            extras.remove(KNOWN_PART_KEYS);
            if (!extras.isEmpty()) {
                part.extras = extras;
            }
        }
        return part;
    }

    private static final List<String> KNOWN_PART_KEYS = List.of(
            "type", "suffix", "name", "mirror", "outline", "drawRegion", "under", "clampProgress",
            "turretShading", "editorVisible", "id", "label",
            "x", "y", "originX", "originY", "rotation", "xScl", "yScl",
            "moveX", "moveY", "moveRot", "growX", "growY",
            "layer", "layerOffset", "outlineLayerOffset", "heatLayerOffset",
            "progress", "heatProgress", "growProgress", "moves", "children", "animations"
    );

    private static ObjectNode moveToJson(PartMoveModel move) {
        ObjectNode node = move.extras == null ? MAPPER.createObjectNode() : move.extras.deepCopy();
        if (move.progress != null) {
            node.set("progress", move.progress);
        }
        putNum(node, "x", move.x);
        putNum(node, "y", move.y);
        putNum(node, "gx", move.gx);
        putNum(node, "gy", move.gy);
        putNum(node, "rot", move.rot);
        return node;
    }

    private static PartMoveModel moveFromJson(JsonNode node) {
        PartMoveModel move = new PartMoveModel();
        move.progress = node.get("progress");
        move.x = f(node, "x");
        move.y = f(node, "y");
        move.gx = f(node, "gx");
        move.gy = f(node, "gy");
        move.rot = f(node, "rot");
        if (node instanceof ObjectNode object) {
            ObjectNode extras = object.deepCopy();
            extras.remove(List.of("progress", "x", "y", "gx", "gy", "rot"));
            if (!extras.isEmpty()) {
                move.extras = extras;
            }
        }
        return move;
    }

    private static ObjectNode animationToJson(ChannelAnimation channel) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("channel", channel.channel);
        var tracks = node.putArray("tracks");
        for (AnimTrack track : channel.tracks) {
            ObjectNode trackNode = tracks.addObject();
            trackNode.put("property", track.property);
            var keys = trackNode.putArray("keys");
            for (Keyframe key : track.keys) {
                ObjectNode keyNode = keys.addObject();
                keyNode.put("t", num(key.time));
                keyNode.put("v", num(key.value));
                keyNode.put("interpolation", key.interpolation == null ? "linear" : key.interpolation);
            }
        }
        return node;
    }

    private static ChannelAnimation animationFromJson(JsonNode node) {
        ChannelAnimation channel = new ChannelAnimation(node.path("channel").asText("warmup"));
        JsonNode tracks = node.get("tracks");
        if (tracks != null && tracks.isArray()) {
            for (JsonNode trackNode : tracks) {
                AnimTrack track = new AnimTrack(trackNode.path("property").asText("x"));
                JsonNode keys = trackNode.get("keys");
                if (keys != null && keys.isArray()) {
                    for (JsonNode keyNode : keys) {
                        Keyframe key = new Keyframe((float) keyNode.path("t").asDouble(), (float) keyNode.path("v").asDouble());
                        key.interpolation = keyNode.path("interpolation").asText("linear");
                        track.keys.add(key);
                    }
                }
                channel.tracks.add(track);
            }
        }
        return channel;
    }

    private static float f(JsonNode node, String field) {
        return node.has(field) ? (float) node.get(field).asDouble() : 0f;
    }

    private static void putNum(ObjectNode node, String field, float value) {
        node.put(field, num(value));
    }

    private static double num(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    public static List<String> missingSprites(Em3Project project) {
        List<String> missing = new ArrayList<>();
        for (String region : project.sprites.keySet()) {
            Path path = project.resolveSprite(region);
            if (path == null || !Files.isRegularFile(path)) {
                missing.add(region);
            }
        }
        return missing;
    }
}
