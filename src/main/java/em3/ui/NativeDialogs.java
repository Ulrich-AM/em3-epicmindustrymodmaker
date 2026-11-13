package em3.ui;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Optional native file dialogs. The editor still accepts a typed path when the dialog is unavailable. */
public final class NativeDialogs {
    private NativeDialogs() {
    }

    public static String pick(String kind, String current) {
        try {
            if ("export".equals(kind)) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer filters = stack.mallocPointer(1);
                    filters.put(stack.UTF8("*.json"));
                    filters.flip();
                    return TinyFileDialogs.tinyfd_saveFileDialog("Export JSON", current, filters, "JSON");
                }
            }
            if ("open".equals(kind) || "import".equals(kind) || "save".equals(kind)) {
                return TinyFileDialogs.tinyfd_selectFolderDialog("Choose folder", current);
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }
}
