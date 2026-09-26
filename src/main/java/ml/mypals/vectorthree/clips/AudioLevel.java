package ml.mypals.vectorthree.clips;

/** Volume and pitch, on Flashback's AudioKeyframe and on the change it plays through. */
public interface AudioLevel {
    float vector3$volume();

    float vector3$pitch();

    void vector3$setLevel(float volume, float pitch);

    final class Playing {
        public static float volume = 1;

        private Playing() {}
    }
}
