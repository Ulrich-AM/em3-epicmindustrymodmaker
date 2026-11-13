package em3.app;

import java.util.ArrayList;
import java.util.List;

public final class EditorLog {
    public final List<String> lines = new ArrayList<>();

    public void info(String message) {
        lines.add(message);
        trim();
    }

    public void error(String message) {
        lines.add("Error: " + message);
        trim();
    }

    private void trim() {
        while (lines.size() > 400) {
            lines.remove(0);
        }
    }
}
