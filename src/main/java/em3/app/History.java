package em3.app;

import em3.project.RegionPartModel;
import em3.project.TurretContent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Snapshot undo for part and keyframe edits. One gizmo drag is one entry. */
public final class History {
    private final Deque<List<RegionPartModel>> undo = new ArrayDeque<>();
    private final Deque<List<RegionPartModel>> redo = new ArrayDeque<>();
    private List<RegionPartModel> pending;

    public void begin(TurretContent turret) {
        if (pending == null) {
            pending = capture(turret);
        }
    }

    public void commit(TurretContent turret) {
        if (pending == null) {
            return;
        }
        List<RegionPartModel> after = capture(turret);
        if (!same(pending, after)) {
            undo.push(pending);
            redo.clear();
            while (undo.size() > 50) {
                undo.removeLast();
            }
        }
        pending = null;
    }

    public void cancel(TurretContent turret) {
        if (pending != null) {
            restore(turret, pending);
            pending = null;
        }
    }

    public void pushNow(TurretContent turret, List<RegionPartModel> before) {
        undo.push(before);
        redo.clear();
        pending = null;
    }

    public boolean undo(TurretContent turret) {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(capture(turret));
        restore(turret, undo.pop());
        return true;
    }

    public boolean redo(TurretContent turret) {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(capture(turret));
        restore(turret, redo.pop());
        return true;
    }

    public static List<RegionPartModel> capture(TurretContent turret) {
        List<RegionPartModel> copy = new ArrayList<>();
        for (RegionPartModel part : turret.parts) {
            copy.add(part.copy());
        }
        return copy;
    }

    private static void restore(TurretContent turret, List<RegionPartModel> parts) {
        turret.parts.clear();
        for (RegionPartModel part : parts) {
            turret.parts.add(part.copy());
        }
    }

    private static boolean same(List<RegionPartModel> a, List<RegionPartModel> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!describe(a.get(i)).equals(describe(b.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String describe(RegionPartModel part) {
        StringBuilder text = new StringBuilder();
        text.append(part.x).append(',').append(part.y).append(',').append(part.rotation)
                .append(',').append(part.xScl).append(',').append(part.yScl)
                .append(',').append(part.moveX).append(',').append(part.moveY).append(',').append(part.moveRot);
        for (var channel : part.animations) {
            text.append('|').append(channel.channel);
            for (var track : channel.tracks) {
                text.append('/').append(track.property);
                for (var key : track.keys) {
                    text.append('@').append(key.time).append('=').append(key.value).append(key.interpolation);
                }
            }
        }
        return text.toString();
    }
}
