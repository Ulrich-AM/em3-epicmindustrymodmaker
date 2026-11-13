package em3.animation;

import com.fasterxml.jackson.databind.JsonNode;
import em3.export.ExportReport;

/**
 * Evaluates Mindustry {@code PartProgress} values the way {@code ContentParser.parseProgressOp} builds them.
 * Sine operations use {@link Math#sin} rather than Arc's lookup table. The difference is called out in the report
 * only when a sine operation is actually present.
 */
public final class ProgressEval {
    private ProgressEval() {
    }

    public static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static float eval(JsonNode node, PartParams params, float ticks, boolean clamp, ExportReport report) {
        float raw = evalRaw(node, params, ticks, report);
        return clamp ? clamp01(raw) : raw;
    }

    public static float evalRaw(JsonNode node, PartParams params, float ticks, ExportReport report) {
        if (node == null || node.isNull()) {
            return params.warmup;
        }
        if (node.isNumber()) {
            return node.floatValue();
        }
        if (node.isTextual()) {
            return ref(node.asText(), params, ticks, report);
        }
        if (!node.isObject()) {
            if (report != null) {
                report.error("PartProgress value must be a string, number, or object");
            }
            return 0f;
        }
        if (!node.has("type")) {
            if (report != null) {
                report.error("PartProgress object needs a 'type' string field");
            }
            return 0f;
        }
        float base = ref(node.get("type").asText(), params, ticks, report);
        JsonNode ops = node.has("operations") ? node.get("operations") : node.get("ops");
        if (ops != null && ops.isArray()) {
            for (JsonNode step : ops) {
                JsonNode op = step.has("operation") ? step.get("operation") : step.get("op");
                if (op == null || !op.isTextual()) {
                    if (report != null) {
                        report.error("Chained PartProgress operation is missing 'op'");
                    }
                    continue;
                }
                base = apply(base, op.asText(), step, params, ticks, report);
            }
            return base;
        }
        JsonNode op = node.has("operation") ? node.get("operation") : node.get("op");
        if (op == null) {
            return base;
        }
        return apply(base, op.asText(), node, params, ticks, report);
    }

    private static float ref(String name, PartParams params, float ticks, ExportReport report) {
        return switch (name) {
            case "warmup" -> params.warmup;
            case "reload" -> params.reload;
            case "smoothReload" -> params.smoothReload;
            case "heat" -> params.heat;
            case "recoil" -> params.recoil;
            case "charge" -> params.charge;
            case "life" -> params.life;
            case "time" -> ticks;
            default -> {
                if (report != null) {
                    report.error("Unknown PartProgress '" + name + "'");
                }
                yield 0f;
            }
        };
    }

    private static float apply(float base, String op, JsonNode data, PartParams params, float ticks, ExportReport report) {
        return switch (op) {
            case "inv" -> 1f - base;
            case "slope" -> 1f - Math.abs(base - 0.5f) * 2f;
            case "clamp" -> clamp01(base);
            case "delay" -> (base - amount(data, "amount")) / (1f - amount(data, "amount"));
            case "sustain" -> sustain(base, num(data, "offset", 0f), num(data, "grow", 0f), num(data, "sustain", 0f));
            case "shorten" -> base / (1f - amount(data, "amount"));
            case "compress" -> curve(base, num(data, "start", 0f), num(data, "end", 1f));
            case "add" -> data.has("amount")
                    ? base + data.get("amount").floatValue()
                    : base + evalRaw(data.get("other"), params, ticks, report);
            case "blend" -> lerp(base, evalRaw(data.get("other"), params, ticks, report), num(data, "amount", 0f));
            case "mul" -> data.has("amount")
                    ? base * data.get("amount").floatValue()
                    : base * evalRaw(data.get("other"), params, ticks, report);
            case "min" -> Math.min(base, evalRaw(data.get("other"), params, ticks, report));
            case "sin" -> {
                if (report != null) {
                    report.note("PartProgress sin uses Math.sin; Arc uses a lookup table");
                }
                float offset = num(data, "offset", 0f);
                float scl = num(data, "scl", 1f);
                float mag = num(data, "mag", 1f);
                yield base + (float) Math.sin((ticks + offset) / scl) * mag;
            }
            case "absin" -> {
                if (report != null) {
                    report.note("PartProgress absin uses Math.sin; Arc uses a lookup table");
                }
                float scl = num(data, "scl", 1f);
                float mag = num(data, "mag", 1f);
                float s = (float) Math.sin((ticks) / (scl * 2f)) * mag;
                yield base + (s + mag) / 2f;
            }
            case "mod" -> mod(base, amount(data, "amount"));
            case "loop" -> {
                float time = num(data, "time", 1f);
                yield mod(base / time, 1f);
            }
            case "curve" -> {
                if (data.has("interp")) {
                    yield interp(text(data.get("interp")), base, report);
                }
                yield (base - num(data, "offset", 0f)) / num(data, "duration", 1f);
            }
            default -> {
                if (report != null) {
                    report.error("Unknown PartProgress operation '" + op + "'");
                }
                yield base;
            }
        };
    }

    private static float sustain(float value, float offset, float grow, float sustain) {
        float val = value - offset;
        if (grow == 0f) {
            return 0f;
        }
        return Math.min(Math.max(val, 0f) / grow, (grow + sustain + grow - val) / grow);
    }

    private static float curve(float f, float from, float to) {
        if (f < from) {
            return 0f;
        }
        if (f > to) {
            return 1f;
        }
        if (from == to) {
            return 1f;
        }
        return (f - from) / (to - from);
    }

    private static float interp(String name, float f, ExportReport report) {
        String id = name == null ? "linear" : name;
        return switch (id) {
            case "linear", "linearIn", "linearOut" -> f;
            case "pow2", "pow2In", "smooth2" -> f * f;
            case "pow3", "pow3In" -> f * f * f;
            case "smooth", "smoothIn" -> f * f * (3f - 2f * f);
            case "sine", "sineIn" -> 1f - (float) Math.cos(f * Math.PI / 2.0);
            case "sineOut" -> (float) Math.sin(f * Math.PI / 2.0);
            default -> {
                if (report != null) {
                    report.warn("Interp '" + id + "' is not in the Phase 1 set; linear was used");
                }
                yield f;
            }
        };
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float mod(float f, float n) {
        if (n == 0f) {
            return 0f;
        }
        return ((f % n) + n) % n;
    }

    private static float amount(JsonNode data, String field) {
        return data != null && data.has(field) ? data.get(field).floatValue() : 0f;
    }

    private static float num(JsonNode data, String field, float fallback) {
        return data != null && data.has(field) && data.get(field).isNumber() ? data.get(field).floatValue() : fallback;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "linear";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject() && node.has("type")) {
            return node.get("type").asText();
        }
        return node.asText();
    }
}
