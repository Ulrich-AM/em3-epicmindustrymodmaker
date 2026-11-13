package em3.animation;

import com.fasterxml.jackson.databind.JsonNode;
import em3.export.ExportReport;
import em3.mindustry.MindustryV160;
import em3.app.ShadowDebug;
import em3.project.RegionPartModel;
import em3.project.SpriteSize;
import em3.project.TurretContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Evaluates {@code RegionPart.draw} and the turret base / shadow placement from {@code DrawTurret.draw}.
 * Editor overlay visibility is applied by the caller. This class always reports what the game would draw.
 */
public final class RegionMath {
    private RegionMath() {
    }

    public static List<SpritePlacement> compose(TurretContent turret, PartParams params, float ticks,
                                                 Function<String, SpriteSize> sprites, ExportReport report) {
        return compose(turret, params, ticks, sprites, report, null);
    }

    public static List<SpritePlacement> compose(TurretContent turret, PartParams params, float ticks,
                                                 Function<String, SpriteSize> sprites, ExportReport report,
                                                 ShadowDebug shadowDebug) {
        List<SpritePlacement> list = new ArrayList<>();
        int[] order = {0};
        float elevation = MindustryV160.elevationFor(turret.size, turret.elevation);
        float recoilLen = -((float) Math.pow(Math.max(0f, params.recoil), turret.recoilPow)) * turret.blockRecoil;
        double aim = Math.toRadians(params.rotation);
        float recoilX = recoilLen * (float) Math.cos(aim);
        float recoilY = recoilLen * (float) Math.sin(aim);
        float drawRot = params.rotation - 90f;
        float shadowX = shadowDebug == null ? -elevation : shadowDebug.offsetX(elevation);
        float shadowY = shadowDebug == null ? -elevation : shadowDebug.offsetY(elevation);
        float shadowAlpha = shadowDebug == null ? MindustryV160.SHADOW_ALPHA : shadowDebug.opacity;

        SpriteSize baseSize = sprites.apply(turret.baseRegion);
        SpritePlacement base = centered(turret.baseRegion, "base", "Base", "base",
                params.x, params.y, 0f, baseSize, 1f, MindustryV160.LAYER_BLOCK, order[0]++);
        base.selectionKey = "base";
        list.add(base);

        // DrawTurret calls Drawf.shadow(preview, x + recoil - elevation, y + recoil - elevation, drawrot).
        // The elevation shift is in world axes, not along the aim. Pal.shadow is black at alpha 0.22.
        SpriteSize previewSize = sprites.apply(turret.previewRegion);
        SpritePlacement shadow = centered(turret.previewRegion, "preview", "Main turret body", "shadow",
                params.x + recoilX + shadowX, params.y + recoilY + shadowY, drawRot, previewSize, 1f,
                MindustryV160.LAYER_SHADOW, order[0]++);
        shadow.selectionKey = "preview";
        shadow.r = 0f;
        shadow.g = 0f;
        shadow.b = 0f;
        shadow.a = shadowAlpha;
        list.add(shadow);

        PartParams root = params.copy();
        root.x = params.x + recoilX;
        root.y = params.y + recoilY;
        for (RegionPartModel part : turret.parts) {
            drawPart(part, turret.name, root, ticks, sprites, report, list, order, true);
        }
        list.sort(Comparator.comparingDouble((SpritePlacement s) -> s.z).thenComparingInt(s -> s.order));
        return list;
    }

    private static void drawPart(RegionPartModel part, String blockName, PartParams params, float ticks,
                                 Function<String, SpriteSize> sprites, ExportReport report,
                                 List<SpritePlacement> out, int[] order, boolean turretShading) {
        if (!part.editorVisible) {
            return;
        }
        boolean shading = part.turretShading && turretShading;
        float prog = ProgressEval.eval(part.progress, params, ticks, part.clampProgress, report);
        float sclProg = ProgressEval.eval(part.growProgress, params, ticks, part.clampProgress, report);
        String progressName = plainProgress(part.progress);
        float mx = animated(part, progressName, "x", prog, part.moveX, params);
        float my = animated(part, progressName, "y", prog, part.moveY, params);
        float mr = animated(part, progressName, "rotation", prog, part.moveRot, params) + part.rotation;
        float gx = part.growX * sclProg;
        float gy = part.growY * sclProg;
        gx += scaleTrack(part, progressName, "xScl", params);
        gy += scaleTrack(part, progressName, "yScl", params);
        for (var channel : part.animations) {
            if (channel.channel.equals(progressName)) {
                continue;
            }
            float t = params.channel(channel.channel);
            if (Float.isNaN(t)) {
                continue;
            }
            if (part.hasTrack(channel.channel, "x")) {
                mx += part.sampleTrack(channel.channel, "x", t);
            }
            if (part.hasTrack(channel.channel, "y")) {
                my += part.sampleTrack(channel.channel, "y", t);
            }
            if (part.hasTrack(channel.channel, "rotation")) {
                mr += part.sampleTrack(channel.channel, "rotation", t);
            }
            if (part.hasTrack(channel.channel, "xScl")) {
                gx += part.sampleTrack(channel.channel, "xScl", t);
            }
            if (part.hasTrack(channel.channel, "yScl")) {
                gy += part.sampleTrack(channel.channel, "yScl", t);
            }
        }
        for (var move : part.moves) {
            String moveChannel = plainProgress(move.progress);
            float p = ProgressEval.eval(move.progress, params, ticks, part.clampProgress, report);
            if (moveChannel == null || !part.hasTrack(moveChannel, "x")) {
                mx += move.x * p;
            }
            if (moveChannel == null || !part.hasTrack(moveChannel, "y")) {
                my += move.y * p;
            }
            if (moveChannel == null || !part.hasTrack(moveChannel, "rotation")) {
                mr += move.rot * p;
            }
            if (moveChannel == null || !part.hasTrack(moveChannel, "xScl")) {
                gx += move.gx * p;
            }
            if (moveChannel == null || !part.hasTrack(moveChannel, "yScl")) {
                gy += move.gy * p;
            }
        }

        float baseXscl = part.xScl + gx;
        float baseYscl = part.yScl + gy;
        float z = part.layer > 0f ? part.layer : MindustryV160.LAYER_TURRET;
        if (part.under && shading) {
            z -= 0.0001f;
        }
        z += part.layerOffset;
        float outlineZ = z + part.outlineLayerOffset;
        float heatZ = shading ? MindustryV160.LAYER_TURRET_HEAT : z + part.heatLayerOffset;

        String realName = part.name != null && !part.name.isBlank() ? part.name : blockName + part.suffix;
        int len = part.mirror && params.sideOverride == -1 ? 2 : 1;
        float[] childMx = new float[2];
        float[] childMy = new float[2];
        float[] childRot = new float[2];
        int[] childSide = new int[2];

        for (int s = 0; s < len; s++) {
            int i = params.sideOverride == -1 ? s : params.sideOverride;
            float sign = (i == 0 ? 1f : -1f) * params.sideMultiplier;
            float drawXscl = baseXscl * sign;
            float drawYscl = baseYscl;
            float[] off = rotateRadExact((part.x + mx) * sign, part.y + my, (float) Math.toRadians(params.rotation - 90f));
            if (part.originX != 0f || part.originY != 0f) {
                float[] shifted = rotateDeg(-part.originX * drawXscl, -part.originY * drawYscl, params.rotation - 90f);
                shifted[0] += part.originX * drawXscl;
                shifted[1] += part.originY * drawYscl;
                off[0] -= shifted[0];
                off[1] -= shifted[1];
            }
            float cx = params.x + off[0];
            float cy = params.y + off[1];
            float rot = mr * sign + params.rotation - 90f;
            childMx[s] = cx;
            childMy[s] = cy;
            childRot[s] = mr * sign + params.rotation;
            childSide[s] = i;

            String regionName;
            String outlineName;
            if (part.mirror && shading) {
                regionName = realName + (i == 0 ? "-r" : "-l");
                outlineName = regionName + "-outline";
            } else {
                regionName = realName;
                outlineName = realName + "-outline";
            }
            String heatName = realName + "-heat";
            String sideLabel = !part.mirror ? part.label : (i == 0 ? part.label + " right" : part.label + " left");
            String key = "part/" + part.id + (part.mirror ? "/side/" + i : "");

            if (part.outline && part.drawRegion) {
                SpriteSize size = sprites.apply(outlineName);
                if (size == null || !size.found()) {
                    size = outlineFallback(sprites.apply(regionName));
                }
                SpritePlacement outline = placed(part, key, sideLabel + " outline", outlineName, "outline", i,
                        cx, cy, rot, size, drawXscl, drawYscl, part.originX, part.originY, outlineZ, order[0]++);
                outline.missing = size == null || !size.found() && !size.generated();
                out.add(outline);
            }
            if (part.drawRegion) {
                SpriteSize size = sprites.apply(regionName);
                SpritePlacement region = placed(part, key, sideLabel, regionName, "region", i,
                        cx, cy, rot, size, drawXscl, drawYscl, part.originX, part.originY, z, order[0]++);
                out.add(region);
            }
            SpriteSize heatSize = sprites.apply(heatName);
            if (heatSize != null && heatSize.found()) {
                SpritePlacement heat = placed(part, "part/" + part.id + "/heat", part.label + " heat", heatName, "heat", i,
                        cx, cy, rot, heatSize, drawXscl, drawYscl, part.originX, part.originY, heatZ, order[0]++);
                // Drawf.additive multiplies by heatColor with alpha = heatProgress * heatColor.a, then again by the 1f alpha argument.
                float hprog = ProgressEval.eval(part.heatProgress, params, ticks, part.clampProgress, report);
                heat.r = ((MindustryV160.TURRET_HEAT_ARGB >> 16) & 0xff) / 255f;
                heat.g = ((MindustryV160.TURRET_HEAT_ARGB >> 8) & 0xff) / 255f;
                heat.b = (MindustryV160.TURRET_HEAT_ARGB & 0xff) / 255f;
                heat.a = hprog;
                heat.additive = true;
                // Heat origin includes region.scl(); the color region origin does not. Rebuild corners when origin is non-zero.
                if (part.originX != 0f || part.originY != 0f) {
                    float w = heat.width;
                    float h = heat.height;
                    float ox = w / 2f + part.originX * MindustryV160.SPRITE_SCALE * drawXscl;
                    float oy = h / 2f + part.originY * MindustryV160.SPRITE_SCALE * drawYscl;
                    Quad.place(heat.corners, cx - w / 2f, cy - h / 2f, ox, oy, w, h, rot);
                    heat.originX = ox;
                    heat.originY = oy;
                }
                out.add(heat);
            }
        }

        for (int s = 0; s < len; s++) {
            for (RegionPartModel child : part.children) {
                PartParams childParams = params.copy();
                childParams.x = childMx[s];
                childParams.y = childMy[s];
                childParams.rotation = childRot[s];
                childParams.sideOverride = childSide[s];
                childParams.sideMultiplier = params.sideMultiplier;
                drawPart(child, blockName, childParams, ticks, sprites, report, out, order, shading);
            }
        }
    }

    private static SpriteSize outlineFallback(SpriteSize region) {
        if (region == null) {
            return SpriteSize.missing();
        }
        // Generated outline uses the same canvas as the source sprite. Mark it generated so the renderer can build pixels.
        return SpriteSize.generated(region.width(), region.height(), region.name());
    }

    private static SpritePlacement placed(RegionPartModel part, String key, String label, String sprite, String role, int side,
                                          float cx, float cy, float rot, SpriteSize size, float drawXscl, float drawYscl,
                                          float originX, float originY, float z, int order) {
        SpriteSize used = size == null ? SpriteSize.missing() : size;
        float w = used.width() * MindustryV160.SPRITE_SCALE * drawXscl;
        float h = used.height() * MindustryV160.SPRITE_SCALE * drawYscl;
        float ox = w / 2f + originX * drawXscl;
        float oy = h / 2f + originY * drawYscl;
        SpritePlacement p = new SpritePlacement();
        p.partId = part.id;
        p.selectionKey = key;
        p.label = label;
        p.sprite = sprite;
        p.side = side;
        p.role = role;
        p.z = z;
        p.order = order;
        p.centerX = cx;
        p.centerY = cy;
        p.rotation = rot;
        p.width = w;
        p.height = h;
        p.originX = ox;
        p.originY = oy;
        p.missing = !used.found() && !used.generated();
        p.buildCorners();
        return p;
    }

    private static SpritePlacement centered(String sprite, String partId, String label, String role,
                                            float cx, float cy, float rot, SpriteSize size, float scl,
                                            float z, int order) {
        SpriteSize used = size == null ? SpriteSize.missing() : size;
        float w = used.width() * MindustryV160.SPRITE_SCALE * scl;
        float h = used.height() * MindustryV160.SPRITE_SCALE * scl;
        SpritePlacement p = new SpritePlacement();
        p.partId = partId;
        p.label = label;
        p.sprite = sprite;
        p.role = role;
        p.z = z;
        p.order = order;
        p.centerX = cx;
        p.centerY = cy;
        p.rotation = rot;
        p.width = w;
        p.height = h;
        p.originX = w / 2f;
        p.originY = h / 2f;
        p.missing = !used.found();
        p.buildCorners();
        return p;
    }

    private static float scaleTrack(RegionPartModel part, String channel, String property, PartParams params) {
        if (channel == null || !part.hasTrack(channel, property)) {
            return 0f;
        }
        float sampled = part.sampleTrack(channel, property, params.channel(channel));
        return Float.isNaN(sampled) ? 0f : sampled;
    }

    private static float animated(RegionPartModel part, String channel, String property, float progress,
                                  float classic, PartParams params) {
        if (channel != null && part.hasTrack(channel, property)) {
            float sampled = part.sampleTrack(channel, property, params.channel(channel));
            return Float.isNaN(sampled) ? classic * progress : sampled;
        }
        return classic * progress;
    }

    static String plainProgress(JsonNode node) {
        if (node != null && node.isTextual()) {
            String name = node.asText();
            return switch (name) {
                case "warmup", "reload", "smoothReload", "heat", "recoil", "charge", "life" -> name;
                default -> null;
            };
        }
        return null;
    }

    static float[] rotateRadExact(float x, float y, float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[]{x * cos - y * sin, x * sin + y * cos};
    }

    static float[] rotateDeg(float x, float y, float degrees) {
        return rotateRadExact(x, y, (float) Math.toRadians(degrees));
    }

    /** Local +X / +Y axes of a turret, in world units, after the draw rotation used by RegionPart. */
    /** Part.x change for a world-space drag. The axis already points the way the sprite moves. */
    public static float moveXDelta(float worldDx, float worldDy, float turretRotation, float sign) {
        float[] axis = localAxis(turretRotation, sign);
        return worldDx * axis[0] + worldDy * axis[1];
    }

    public static float moveYDelta(float worldDx, float worldDy, float turretRotation, float sign) {
        float[] axis = localAxis(turretRotation, sign);
        return worldDx * axis[2] + worldDy * axis[3];
    }

    /** Visual rotation change, degrees, from the start pointer offset to the current one. */
    public static float rotationDelta(float startX, float startY, float nowX, float nowY) {
        double a = Math.atan2(startY, startX);
        double b = Math.atan2(nowY, nowX);
        double d = Math.toDegrees(b - a);
        if (d > 180) {
            d -= 360;
        }
        if (d < -180) {
            d += 360;
        }
        return (float) d;
    }

    public static float snap(float value, float step) {
        if (step <= 0f) {
            return value;
        }
        return Math.round(value / step) * step;
    }

    public static float[] localAxis(float turretRotation, float sign) {
        float[] x = rotateRadExact(sign, 0f, (float) Math.toRadians(turretRotation - 90f));
        float[] y = rotateRadExact(0f, 1f, (float) Math.toRadians(turretRotation - 90f));
        return new float[]{x[0], x[1], y[0], y[1]};
    }

    public static JsonNode progressOrWarmup(JsonNode node) {
        return node;
    }
}
