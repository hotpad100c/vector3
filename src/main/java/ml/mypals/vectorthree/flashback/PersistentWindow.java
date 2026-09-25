package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.Flashback;
import imgui.moulberry90.type.ImBoolean;

import java.util.Set;

public final class PersistentWindow {
    private final String id;
    private final ImBoolean open = new ImBoolean(false);
    private boolean loaded;

    public PersistentWindow(String id) {
        this.id = id;
    }

    public String title(String name) {
        return name + "###" + id;
    }

    public ImBoolean open() {
        if (!loaded) {
            loaded = true;
            open.set(windows().contains(id));
        }
        return open;
    }

    public boolean isOpen() {
        return open().get();
    }

    public void toggle() {
        open().set(!open.get());
        sync();
    }

    public void sync() {
        Set<String> windows = windows();
        boolean changed = open.get() ? windows.add(id) : windows.remove(id);
        if (changed) Flashback.getConfig().delayedSaveToDefaultFolder();
    }

    private static Set<String> windows() {
        return Flashback.getConfig().internal.openedWindows;
    }
}
