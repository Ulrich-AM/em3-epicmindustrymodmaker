package em3.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import em3.animation.AnimTrack;
import em3.animation.ChannelAnimation;
import em3.animation.Keyframe;
import em3.animation.TrackMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor part. Fields match {@code RegionPart} public fields that ContentParser assigns.
 * {@code editorVisible} is not exported. {@code extras} keeps JSON keys the inspector does not edit.
 */
public final class RegionPartModel {
    public String id = "part";
    public String label = "Part";
    public String suffix = "";
    public String name;
    public boolean mirror;
    public boolean outline = true;
    public boolean drawRegion = true;
    public boolean turretShading = true;
    public boolean under;
    public boolean editorVisible = true;
    public boolean clampProgress = true;
    public float x;
    public float y;
    public float originX;
    public float originY;
    public float rotation;
    public float xScl = 1f;
    public float yScl = 1f;
    public float moveX;
    public float moveY;
    public float moveRot;
    public float growX;
    public float growY;
    public float layer = -1f;
    public float layerOffset;
    public float outlineLayerOffset = -0.001f;
    public float heatLayerOffset = 1f;
    public JsonNode progress;
    public JsonNode heatProgress;
    public JsonNode growProgress;
    public final List<PartMoveModel> moves = new ArrayList<>();
    public final List<RegionPartModel> children = new ArrayList<>();
    /** Editor tracks. Export turns supported linear keys into PartMoves. */
    public final List<ChannelAnimation> animations = new ArrayList<>();
    public ObjectNode extras;

    public RegionPartModel copy() {
        RegionPartModel c = new RegionPartModel();
        c.id = id;
        c.label = label;
        c.suffix = suffix;
        c.name = name;
        c.mirror = mirror;
        c.outline = outline;
        c.drawRegion = drawRegion;
        c.turretShading = turretShading;
        c.under = under;
        c.editorVisible = editorVisible;
        c.clampProgress = clampProgress;
        c.x = x;
        c.y = y;
        c.originX = originX;
        c.originY = originY;
        c.rotation = rotation;
        c.xScl = xScl;
        c.yScl = yScl;
        c.moveX = moveX;
        c.moveY = moveY;
        c.moveRot = moveRot;
        c.growX = growX;
        c.growY = growY;
        c.layer = layer;
        c.layerOffset = layerOffset;
        c.outlineLayerOffset = outlineLayerOffset;
        c.heatLayerOffset = heatLayerOffset;
        c.progress = progress == null ? null : progress.deepCopy();
        c.heatProgress = heatProgress == null ? null : heatProgress.deepCopy();
        c.growProgress = growProgress == null ? null : growProgress.deepCopy();
        for (PartMoveModel move : moves) {
            c.moves.add(move.copy());
        }
        for (RegionPartModel child : children) {
            c.children.add(child.copy());
        }
        for (ChannelAnimation channel : animations) {
            c.animations.add(channel.copy());
        }
        c.extras = extras == null ? null : extras.deepCopy();
        return c;
    }

    public ChannelAnimation channel(String name) {
        for (ChannelAnimation channel : animations) {
            if (name.equals(channel.channel)) {
                return channel;
            }
        }
        return null;
    }

    public ChannelAnimation channelOrCreate(String name) {
        ChannelAnimation found = channel(name);
        if (found != null) {
            return found;
        }
        ChannelAnimation created = new ChannelAnimation(name);
        animations.add(created);
        return created;
    }

    public boolean hasTrack(String channelName, String property) {
        ChannelAnimation channel = channel(channelName);
        return channel != null && channel.track(property) != null && !channel.track(property).keys.isEmpty();
    }

    public float sampleTrack(String channelName, String property, float time) {
        ChannelAnimation channel = channel(channelName);
        if (channel == null) {
            return Float.NaN;
        }
        AnimTrack track = channel.track(property);
        if (track == null || track.keys.isEmpty()) {
            return Float.NaN;
        }
        return TrackMath.sample(track, time);
    }

    public Keyframe upsertKey(String channelName, String property, float time, float value) {
        return TrackMath.upsert(channelOrCreate(channelName).trackOrCreate(property), time, value);
    }
}
