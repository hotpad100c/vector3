package ml.mypals.vectorthree.core.port;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Work the editor side wants done at points of the render loop. Render code fires them; the editor registers them. */
public final class FrameHooks {
    private static final List<Runnable> BEFORE_FRAME = new CopyOnWriteArrayList<>();

    private FrameHooks() {}

    /** Runs at the start of every rendered frame, in registration order. */
    public static void beforeFrame(Runnable hook) {
        BEFORE_FRAME.add(hook);
    }

    public static void runBeforeFrame() {
        for (Runnable hook : BEFORE_FRAME) hook.run();
    }
}
