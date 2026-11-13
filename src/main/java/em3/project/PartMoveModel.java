package em3.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** One {@code PartMove}. Unknown JSON fields stay on {@link #extras}. */
public final class PartMoveModel {
    public JsonNode progress;
    public float x;
    public float y;
    public float gx;
    public float gy;
    public float rot;
    public ObjectNode extras;

    public PartMoveModel copy() {
        PartMoveModel c = new PartMoveModel();
        c.progress = progress == null ? null : progress.deepCopy();
        c.x = x;
        c.y = y;
        c.gx = gx;
        c.gy = gy;
        c.rot = rot;
        c.extras = extras == null ? null : extras.deepCopy();
        return c;
    }
}
