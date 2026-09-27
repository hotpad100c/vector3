package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.state.EditorScene;

public final class PlaybackRange {
    public interface Holder {
        int vector3$inTick();
        int vector3$outTick();
        void vector3$setInTick(int tick);
        void vector3$setOutTick(int tick);
    }

    private PlaybackRange() {}
    public static int in(EditorScene scene) { return ((Holder) scene).vector3$inTick(); }
    public static int out(EditorScene scene) { return ((Holder) scene).vector3$outTick(); }
    public static void setIn(EditorScene scene, int tick) {
        Holder holder = (Holder) scene;
        holder.vector3$setInTick(Math.max(0, tick));
        if (holder.vector3$outTick() >= 0 && holder.vector3$outTick() <= tick) holder.vector3$setOutTick(-1);
    }
    public static void setOut(EditorScene scene, int tick) {
        Holder holder = (Holder) scene;
        holder.vector3$setOutTick(Math.max(0, tick));
        if (holder.vector3$inTick() >= tick) holder.vector3$setInTick(-1);
    }
}
