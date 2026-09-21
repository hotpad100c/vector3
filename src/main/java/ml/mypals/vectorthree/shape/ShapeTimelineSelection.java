package ml.mypals.vectorthree.shape;

public final class ShapeTimelineSelection {
    private static String requestedShapeId;

    private ShapeTimelineSelection() {}

    public static void request(String shapeId) {
        requestedShapeId = shapeId;
    }

    public static String consume() {
        String shapeId = requestedShapeId;
        requestedShapeId = null;
        return shapeId;
    }
}
