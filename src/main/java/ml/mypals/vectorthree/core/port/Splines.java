package ml.mypals.vectorthree.core.port;

import java.util.Map;

/** Flashback's keyframe interpolation, so shape values ease the same way camera values do. */
public interface Splines {
    double catmullRom(float p0, float p1, float p2, float p3, float t1, float t2, float t3, float amount);

    double hermite(Map<Float, Double> points, float at);
}
