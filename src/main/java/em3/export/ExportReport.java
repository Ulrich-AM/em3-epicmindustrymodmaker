package em3.export;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ExportReport {
    public final List<String> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final Set<String> notes = new LinkedHashSet<>();

    public void error(String message) {
        if (!errors.contains(message)) {
            errors.add(message);
        }
    }

    public void warn(String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    public void note(String message) {
        notes.add(message);
    }

    public boolean ok() {
        return errors.isEmpty();
    }
}
