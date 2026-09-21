package ml.mypals.vectorthree.shape;

public final class ShapeTimelineSelection {
    private static String requestedShapeId;
    private static boolean refreshRequested;

    private ShapeTimelineSelection() {}

    public static void request(String shapeId) {
        requestedShapeId = shapeId;
    }

    public static String consume() {
        String shapeId = requestedShapeId;
        requestedShapeId = null;
        return shapeId;
    }

    public static void requestRefresh() {
        refreshRequested = true;
    }

    public static boolean consumeRefresh() {
        boolean requested = refreshRequested;
        refreshRequested = false;
        return requested;
    }
}
