package ml.mypals.vectorthree.camera.shake;

public record ShakeParams(float rollFrequency, float rollAmplitude, float positionFrequency,
        float positionX, float positionY, float positionZ, int octaves, float roughness, int seed) {
    public static final ShakeParams DEFAULT = new ShakeParams(1, 0, 1, 0, 0, 0, 1, 0.5f, 0);
    public static final int MAX_OCTAVES = 6;

    public float[] floats() {
        return new float[]{rollFrequency, rollAmplitude, positionFrequency, positionX, positionY, positionZ, roughness};
    }

    public static ShakeParams of(float[] f, int octaves, int seed) {
        return new ShakeParams(f[0], f[1], f[2], f[3], f[4], f[5], Math.clamp(octaves, 1, MAX_OCTAVES),
                Math.clamp(f[6], 0, 1), seed);
    }

    public static ShakeParams orDefault(ShakeParams params) {
        return params == null ? DEFAULT : params;
    }

    public boolean hasPosition() {
        return positionX != 0 || positionY != 0 || positionZ != 0;
    }

    public enum Preset {
        HANDHELD(1.0f, 1.2f, 1.2f, 1.0f, new ShakeParams(0.8f, 0.8f, 1.0f, 0.02f, 0.02f, 0.01f, 3, 0.45f, 0)),
        WALKING(2.5f, 1.5f, 3.0f, 1.5f, new ShakeParams(2.0f, 1.2f, 3.0f, 0.03f, 0.05f, 0.02f, 2, 0.5f, 0)),
        VEHICLE(4.0f, 0.5f, 6.0f, 0.8f, new ShakeParams(5.0f, 0.6f, 7.0f, 0.01f, 0.03f, 0.01f, 3, 0.6f, 0)),
        IMPACT(8.0f, 3.0f, 9.0f, 3.0f, new ShakeParams(8.0f, 2.0f, 9.0f, 0.08f, 0.08f, 0.05f, 4, 0.55f, 0));

        public final float frequencyX, amplitudeX, frequencyY, amplitudeY;
        public final ShakeParams params;

        Preset(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY, ShakeParams params) {
            this.frequencyX = frequencyX;
            this.amplitudeX = amplitudeX;
            this.frequencyY = frequencyY;
            this.amplitudeY = amplitudeY;
            this.params = params;
        }
    }
}
