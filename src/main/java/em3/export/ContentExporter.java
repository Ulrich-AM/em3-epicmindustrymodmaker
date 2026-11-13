package em3.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        put(node, "moveX", part.moveX);
        put(node, "moveY", part.moveY);
        put(node, "moveRot", part.moveRot);
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
