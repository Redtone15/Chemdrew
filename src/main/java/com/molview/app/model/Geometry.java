package com.molview.app.model;

/** Small geometry helpers shared by the model, canvas and exporters. */
public final class Geometry {
    private Geometry() {
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    public static double distanceToSegment(double px, double py,
                                          double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return Math.hypot(px - x1, py - y1);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /** Sample a quadratic Bezier into a compact polyline. */
    public static double[][] quadratic(double x1, double y1, double cx, double cy,
                                       double x2, double y2, int segments) {
        int n = Math.max(2, segments);
        double[][] points = new double[n + 1][2];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double mt = 1 - t;
            points[i][0] = mt * mt * x1 + 2 * mt * t * cx + t * t * x2;
            points[i][1] = mt * mt * y1 + 2 * mt * t * cy + t * t * y2;
        }
        return points;
    }

    public static double distanceToPolyline(double px, double py, double[][] points) {
        double best = Double.MAX_VALUE;
        for (int i = 1; i < points.length; i++) {
            best = Math.min(best, distanceToSegment(px, py,
                    points[i - 1][0], points[i - 1][1], points[i][0], points[i][1]));
        }
        return best;
    }

    public static Bounds polylineBounds(double[][] points) {
        if (points.length == 0) {
            return Bounds.point(0, 0);
        }
        double minX = points[0][0];
        double minY = points[0][1];
        double maxX = minX;
        double maxY = minY;
        for (double[] p : points) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    public static double snapAngle(double angle, double stepRadians) {
        return Math.round(angle / stepRadians) * stepRadians;
    }

    /** Soft snap: only commits to the step when the angle is already close. */
    public static double snapAngleWithin(double angle, double stepRadians,
                                         double toleranceRadians) {
        double snapped = snapAngle(angle, stepRadians);
        double delta = Math.atan2(Math.sin(angle - snapped), Math.cos(angle - snapped));
        return Math.abs(delta) <= toleranceRadians ? snapped : angle;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
