package em3.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import em3.animation.AnimTrack;
import em3.animation.ChannelAnimation;
import em3.animation.TrackMath;
import em3.project.Em3Project;
import em3.project.PartMoveModel;
import em3.project.ProjectStore;
import em3.project.RegionPartModel;
import em3.project.TurretContent;

import java.util.List;

/**
 * Builds a Mindustry content object for the current turret.
 * The document is JSON, which Mindustry's HJSON parser accepts.
 * Fields ContentParser does not read are kept from the part extras and also reported.
 */
public final class ContentExporter {
    private static final List<String> SUPPORTED_PART = List.of(
            "type", "suffix", "name", "mirror", "outline", "drawRegion", "under", "clampProgress",
            "x", "y", "originX", "originY", "rotation", "xScl", "yScl",
            "moveX", "moveY", "moveRot", "growX", "growY",
            "layer", "layerOffset", "outlineLayerOffset", "heatLayerOffset",
            "progress", "heatProgress", "growProgress", "moves", "children",
            "blending", "color", "colorTo", "mixColor", "mixColorTo", "heatColor",
            "heatLight", "heatLightOpacity", "replaceOutline"
    );

    private ContentExporter() {
    }

    public static String export(Em3Project project, ExportReport report) {
        TurretContent turret = project.turret;
        ObjectNode root = turret.extras == null
                ? ProjectStore.mapper().createObjectNode()
                : turret.extras.deepCopy();
        root.put("type", turret.type);
        root.put("name", turret.name);
        root.put("size", turret.size);
        ObjectNode drawer = root.putObject("drawer");
        drawer.put("type", "DrawTurret");
        var parts = drawer.putArray("parts");
        for (RegionPartModel part : turret.parts) {
            parts.add(exportPart(part, report));
        }
        if (!turret.bodyRegion.equals(turret.name)) {
            report.warn("Body region '" + turret.bodyRegion + "' does not match the content name '" + turret.name + "'");
        }
        try {
            return ProjectStore.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
        } catch (Exception e) {
            report.error("Could not format JSON: " + e.getMessage());
            return "{}\n";
        }
    }

    private static ObjectNode exportPart(RegionPartModel part, ExportReport report) {
        ObjectNode node = part.extras == null
                ? ProjectStore.mapper().createObjectNode()
                : part.extras.deepCopy();
        node.remove(List.of("id", "label", "editorVisible", "turretShading"));
        if (node.has("editorVisible")) {
            report.warn("Dropped editor-only field editorVisible from " + part.label);
        }
        node.put("type", "RegionPart");
        node.put("suffix", part.suffix);
        if (part.name != null && !part.name.isBlank()) {
            node.put("name", part.name);
        } else {
            node.remove("name");
        }
        node.put("mirror", part.mirror);
        node.put("outline", part.outline);
        node.put("drawRegion", part.drawRegion);
        if (part.under) {
            node.put("under", true);
        }
        if (!part.clampProgress) {
            node.put("clampProgress", false);
        }
        put(node, "x", part.x);
        put(node, "y", part.y);
        put(node, "originX", part.originX);
        put(node, "originY", part.originY);
        put(node, "rotation", part.rotation);
        if (part.xScl != 1f) {
            put(node, "xScl", part.xScl);
        }
        if (part.yScl != 1f) {
            put(node, "yScl", part.yScl);
        }
        String progressName = part.progress != null && part.progress.isTextual() ? part.progress.asText() : null;
        put(node, "moveX", exportedClassic(part, progressName, "x", part.moveX));
        put(node, "moveY", exportedClassic(part, progressName, "y", part.moveY));
        put(node, "moveRot", exportedClassic(part, progressName, "rotation", part.moveRot));
        if (part.growX != 0f) {
            put(node, "growX", part.growX);
        }
        if (part.growY != 0f) {
            put(node, "growY", part.growY);
        }
        if (part.layer > 0f) {
            put(node, "layer", part.layer);
        }
        if (part.layerOffset != 0f) {
            put(node, "layerOffset", part.layerOffset);
        }
        if (part.outlineLayerOffset != -0.001f) {
            put(node, "outlineLayerOffset", part.outlineLayerOffset);
        }
        if (part.heatLayerOffset != 1f) {
            put(node, "heatLayerOffset", part.heatLayerOffset);
        }
        if (part.progress != null) {
            node.set("progress", part.progress);
            validateProgress(part.progress, part.label, report);
        }
        if (part.heatProgress != null) {
            node.set("heatProgress", part.heatProgress);
            validateProgress(part.heatProgress, part.label + " heat", report);
        }
        if (part.growProgress != null) {
            node.set("growProgress", part.growProgress);
        }
        var moves = node.putArray("moves");
        for (PartMoveModel move : part.moves) {
            if (channelReplaced(part, move.progress)) {
                continue;
            }
            ObjectNode moveNode = move.extras == null
                    ? ProjectStore.mapper().createObjectNode()
                    : move.extras.deepCopy();
            if (move.progress != null) {
                moveNode.set("progress", move.progress);
                validateProgress(move.progress, part.label + " move", report);
            }
            put(moveNode, "x", move.x);
            put(moveNode, "y", move.y);
            if (move.gx != 0f) {
                put(moveNode, "gx", move.gx);
            }
            if (move.gy != 0f) {
                put(moveNode, "gy", move.gy);
            }
            put(moveNode, "rot", move.rot);
            warnUnknown(moveNode, List.of("progress", "x", "y", "gx", "gy", "rot"), part.label + " move", report);
            moves.add(moveNode);
        }
        exportTracks(part, moves, report);
        if (!part.children.isEmpty()) {
            var children = node.putArray("children");
            for (RegionPartModel child : part.children) {
                children.add(exportPart(child, report));
            }
        } else {
            node.remove("children");
        }
        warnUnknown(node, SUPPORTED_PART, part.label, report);
        if (!part.editorVisible) {
            report.note(part.label + " is hidden in the editor. That does not change the exported outline or region.");
        }
        return node;
    }

    private static float exportedClassic(RegionPartModel part, String channel, String property, float fallback) {
        TrackMath.Segment simple = simpleSpan(part, channel, property);
        if (simple == null) {
            return part.hasTrack(channel, property) ? 0f : fallback;
        }
        return simple.delta;
    }

    /** One linear key pair from 0 to 1. That is the same as moveX * progress. */
    private static TrackMath.Segment simpleSpan(RegionPartModel part, String channel, String property) {
        if (channel == null || !part.hasTrack(channel, property)) {
            return null;
        }
        AnimTrack track = part.channel(channel).track(property);
        var segments = TrackMath.segments(track);
        if (segments.size() != 1) {
            return null;
        }
        TrackMath.Segment segment = segments.get(0);
        if (segment.step || segment.start > 0.001f || segment.end < 0.999f) {
            return null;
        }
        return segment;
    }

    private static boolean channelReplaced(RegionPartModel part, JsonNode progress) {
        if (progress == null || !progress.isTextual()) {
            return false;
        }
        ChannelAnimation channel = part.channel(progress.asText());
        return channel != null && !channel.tracks.isEmpty();
    }

    private static void exportTracks(RegionPartModel part, com.fasterxml.jackson.databind.node.ArrayNode moves, ExportReport report) {
        java.util.Map<String, ObjectNode> simpleMoves = new java.util.LinkedHashMap<>();
        for (ChannelAnimation channel : part.animations) {
            for (AnimTrack track : channel.tracks) {
                if (track.keys.size() < 2) {
                    if (track.keys.size() == 1 && track.keys.get(0).value != 0f) {
                        report.warn(part.label + " " + channel.channel + " " + track.property
                                + " has one keyframe. A constant offset is not a PartMove, so it stays in the EM3 project only.");
                    }
                    continue;
                }
                String progressName = part.progress != null && part.progress.isTextual() ? part.progress.asText() : null;
                if (simpleSpan(part, channel.channel, track.property) != null) {
                    if (!channel.channel.equals(progressName)) {
                        ObjectNode moveNode = simpleMoves.computeIfAbsent(channel.channel, name -> {
                            ObjectNode created = ProjectStore.mapper().createObjectNode();
                            created.put("progress", name);
                            return created;
                        });
                        writeDelta(moveNode, track.property, simpleSpan(part, channel.channel, track.property).delta, part, report);
                    }
                    continue;
                }
                for (TrackMath.Segment segment : TrackMath.segments(track)) {
                    if (segment.step) {
                        report.error(part.label + " " + channel.channel + " " + track.property
                                + " uses step interpolation. Mindustry compress is linear, so that segment was not exported.");
                        continue;
                    }
                    if (segment.delta == 0f || segment.end <= segment.start) {
                        continue;
                    }
                    ObjectNode moveNode = ProjectStore.mapper().createObjectNode();
                    ObjectNode progress = moveNode.putObject("progress");
                    progress.put("type", channel.channel);
                    progress.put("op", "compress");
                    progress.put("start", segment.start);
                    progress.put("end", segment.end);
                    if (!writeDelta(moveNode, track.property, segment.delta, part, report)) {
                        continue;
                    }
                    moves.add(moveNode);
                }
            }
        }
        for (ObjectNode moveNode : simpleMoves.values()) {
            moves.add(moveNode);
        }
    }

    private static boolean writeDelta(ObjectNode moveNode, String property, float delta, RegionPartModel part, ExportReport report) {
        switch (property) {
            case "x" -> put(moveNode, "x", delta);
            case "y" -> put(moveNode, "y", delta);
            case "rotation" -> put(moveNode, "rot", delta);
            case "xScl" -> put(moveNode, "gx", delta);
            case "yScl" -> put(moveNode, "gy", delta);
            default -> {
                report.error(part.label + " track property '" + property + "' has no Mindustry field");
                return false;
            }
        }
        return true;
    }

    private static void validateProgress(JsonNode node, String where, ExportReport report) {
        if (node.isTextual()) {
            String name = node.asText();
            if (!List.of("warmup", "reload", "smoothReload", "heat", "recoil", "charge", "life", "time").contains(name)) {
                report.error(where + " uses unknown progress '" + name + "'");
            }
            return;
        }
        if (node.isNumber() || node.isObject()) {
            return;
        }
        report.error(where + " progress is not a string, number, or object");
    }

    private static void warnUnknown(ObjectNode node, List<String> known, String where, ExportReport report) {
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!known.contains(field)) {
                report.warn(where + " keeps unsupported field '" + field + "' so it is not dropped, but Phase 1 does not simulate it");
            }
        }
    }

    private static void put(ObjectNode node, String field, float value) {
        node.put(field, Double.parseDouble(Float.toString(value)));
    }
}
