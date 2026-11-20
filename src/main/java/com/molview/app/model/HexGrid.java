package com.molview.app.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Pointy-top honeycomb lattice used by the sketcher. Atoms are placed on
 * vertices and every bond is one hexagon edge long.
 *
 * <p>Hexagon centers are arranged in staggered rows. The six vertices of a
 * pointy-top hexagon sit at 30°, 90°, 150°, 210°, 270° and 330°. A horizontal
 * alkane chain therefore zig-zags through alternating +30°/−30° edges, while
 * every drawn bond remains exactly {@link #EDGE} units long.</p>
 */
public final class HexGrid {
    /** Length of one hexagonal-grid edge, in drawing-world pixels. */
    public static final double EDGE = 40.0;

    private static final double SQRT3 = Math.sqrt(3.0);
    private static final double COL_STEP = SQRT3 * EDGE;
    private static final double ROW_STEP = 1.5 * EDGE;
    private static final double HALF_COL_STEP = COL_STEP / 2.0;
    private static final double DIST_EPSILON = 0.02;
    private static final double KEY_SCALE = 10_000.0;

    private HexGrid() {
    }

    public record Vertex(double x, double y) {
        public double distanceTo(Vertex other) {
            return Math.hypot(x - other.x, y - other.y);
        }

        public double distanceTo(double px, double py) {
            return Math.hypot(x - px, y - py);
        }
    }

    public record Center(double x, double y) {
    }

    private record Key(long x, long y) {
    }

    private static Key key(double x, double y) {
        return new Key(Math.round(x * KEY_SCALE), Math.round(y * KEY_SCALE));
    }

    public static Center[] centersIn(Bounds bounds) {
        double minX = bounds.minX - EDGE * 2;
        double maxX = bounds.maxX + EDGE * 2;
        double minY = bounds.minY - EDGE * 2;
        double maxY = bounds.maxY + EDGE * 2;
        int minRow = (int) Math.floor(minY / ROW_STEP) - 1;
        int maxRow = (int) Math.ceil(maxY / ROW_STEP) + 1;
        List<Center> centers = new ArrayList<>();
        for (int row = minRow; row <= maxRow; row++) {
            double offset = (row & 1) == 0 ? 0 : HALF_COL_STEP;
            int minCol = (int) Math.floor((minX - offset) / COL_STEP) - 1;
            int maxCol = (int) Math.ceil((maxX - offset) / COL_STEP) + 1;
            for (int col = minCol; col <= maxCol; col++) {
                centers.add(new Center(col * COL_STEP + offset, row * ROW_STEP));
            }
        }
        return centers.toArray(Center[]::new);
    }

    public static Vertex[] verticesOfCenter(double centerX, double centerY) {
        Vertex[] vertices = new Vertex[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(30 + 60 * i);
            vertices[i] = new Vertex(
                    centerX + Math.cos(angle) * EDGE,
                    centerY + Math.sin(angle) * EDGE);
        }
        return vertices;
    }

    private static void addVertices(Map<Key, Vertex> map, double centerX, double centerY) {
        for (Vertex vertex : verticesOfCenter(centerX, centerY)) {
            map.putIfAbsent(key(vertex.x, vertex.y), vertex);
        }
    }

    /** Unique vertices inside (and slightly around) the supplied bounds. */
    public static List<Vertex> verticesIn(Bounds bounds) {
        Map<Key, Vertex> map = new LinkedHashMap<>();
        for (Center center : centersIn(bounds)) {
            addVertices(map, center.x, center.y);
        }
        return new ArrayList<>(map.values());
    }

    /** Nearest honeycomb cell center, used for grid-preserving 180° rotations. */
    public static Center nearestCenter(double x, double y) {
        int rowApprox = (int) Math.round(y / ROW_STEP);
        Center best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int row = rowApprox - 1; row <= rowApprox + 1; row++) {
            double offset = (row & 1) == 0 ? 0 : HALF_COL_STEP;
            int colApprox = (int) Math.round((x - offset) / COL_STEP);
            for (int col = colApprox - 1; col <= colApprox + 1; col++) {
                Center center = new Center(col * COL_STEP + offset, row * ROW_STEP);
                double distance = Math.hypot(center.x - x, center.y - y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = center;
                }
            }
        }
        return best == null ? new Center(x, y) : best;
    }

    /** Snaps any world coordinate to the closest honeycomb vertex. */
    public static Vertex nearestVertex(double x, double y) {
        int rowApprox = (int) Math.round(y / ROW_STEP);
        Vertex best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int row = rowApprox - 2; row <= rowApprox + 2; row++) {
            double offset = (row & 1) == 0 ? 0 : HALF_COL_STEP;
            int colApprox = (int) Math.round((x - offset) / COL_STEP);
            for (int col = colApprox - 2; col <= colApprox + 2; col++) {
                double cx = col * COL_STEP + offset;
                double cy = row * ROW_STEP;
                for (Vertex vertex : verticesOfCenter(cx, cy)) {
                    double distance = vertex.distanceTo(x, y);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = vertex;
                    }
                }
            }
        }
        return best == null ? new Vertex(x, y) : best;
    }

    public static Vertex nearestVertex(Vertex vertex) {
        return nearestVertex(vertex.x, vertex.y);
    }

    public static boolean isVertex(double x, double y, double tolerance) {
        Vertex nearest = nearestVertex(x, y);
        return nearest.distanceTo(x, y) <= tolerance;
    }

    public static boolean isEdge(Vertex a, Vertex b) {
        if (a == null || b == null || a.distanceTo(b) < 1e-6) {
            return false;
        }
        return Math.abs(a.distanceTo(b) - EDGE) <= DIST_EPSILON;
    }

    /**
     * Returns the three honeycomb neighbours of a vertex. The method deliberately
     * works from local candidates instead of a global adjacency map, so it also
     * serves vertices generated on demand by dragging.
     */
    public static List<Vertex> neighbors(Vertex vertex) {
        Bounds area = new Bounds(
                vertex.x - EDGE * 1.4, vertex.y - EDGE * 1.4,
                vertex.x + EDGE * 1.4, vertex.y + EDGE * 1.4);
        Map<Key, Vertex> candidates = new LinkedHashMap<>();
        for (Center center : centersIn(area)) {
            addVertices(candidates, center.x, center.y);
        }
        List<Vertex> result = new ArrayList<>();
        for (Vertex candidate : candidates.values()) {
            double distance = candidate.distanceTo(vertex);
            if (Math.abs(distance - EDGE) <= 0.1) {
                result.add(canonical(candidate));
            }
        }
        result.sort(Comparator.comparingDouble(v -> Math.atan2(v.y - vertex.y, v.x - vertex.x)));
        return result;
    }

    /**
     * Returns the grid neighbour closest to a pointer position. This is used by
     * the bond tool so a drawn bond is always exactly one hexagon edge.
     */
    public static Vertex neighborToward(Vertex start, double x, double y) {
        Vertex best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vertex neighbor : neighbors(start)) {
            double distance = neighbor.distanceTo(x, y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = neighbor;
            }
        }
        return best;
    }

    /**
     * Shortest honeycomb path between two vertices. A* is tie-broken toward the
     * straight line between the endpoints, producing natural zig-zag chains.
     */
    public static List<Vertex> path(Vertex start, Vertex end) {
        if (start == null || end == null) {
            return List.of();
        }
        Vertex source = canonical(nearestVertex(start.x, start.y));
        Vertex target = canonical(nearestVertex(end.x, end.y));
        if (source.distanceTo(target) < 1e-6) {
            return List.of(source);
        }

        Bounds search = new Bounds(
                Math.min(source.x, target.x) - EDGE * 3,
                Math.min(source.y, target.y) - EDGE * 3,
                Math.max(source.x, target.x) + EDGE * 3,
                Math.max(source.y, target.y) + EDGE * 3);
        Map<Key, Vertex> vertices = new LinkedHashMap<>();
        for (Vertex vertex : verticesIn(search)) {
            vertices.put(key(vertex.x, vertex.y), canonical(vertex));
        }
        vertices.putIfAbsent(key(source.x, source.y), source);
        vertices.putIfAbsent(key(target.x, target.y), target);

        Key sourceKey = key(source.x, source.y);
        Key targetKey = key(target.x, target.y);
        double lineLength = source.distanceTo(target);
        double lineX = target.x - source.x;
        double lineY = target.y - source.y;

        Map<Key, Double> gScore = new HashMap<>();
        Map<Key, Key> parent = new HashMap<>();
        Set<Key> closed = new HashSet<>();
        PriorityQueue<PathNode> open = new PriorityQueue<>(
                Comparator.comparingDouble(PathNode::f)
                        .thenComparingDouble(PathNode::deviation)
                        .thenComparingLong(n -> n.key().x)
                        .thenComparingLong(n -> n.key().y));
        gScore.put(sourceKey, 0.0);
        open.add(new PathNode(sourceKey, source, 0.0,
                source.distanceTo(target) / EDGE, 0.0));
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < 20_000) {
            PathNode current = open.poll();
            if (closed.contains(current.key())) {
                continue;
            }
            if (current.key().equals(targetKey)) {
                return reconstruct(vertices, parent, targetKey);
            }
            closed.add(current.key());
            for (Vertex neighbor : neighbors(current.vertex())) {
                Key neighborKey = key(neighbor.x, neighbor.y);
                if (closed.contains(neighborKey) || !vertices.containsKey(neighborKey)) {
                    continue;
                }
                Vertex canonicalNeighbor = vertices.get(neighborKey);
                double tentative = current.g() + 1.0;
                Double known = gScore.get(neighborKey);
                if (known != null && tentative >= known - 1e-9) {
                    continue;
                }
                gScore.put(neighborKey, tentative);
                parent.put(neighborKey, current.key());
                double deviation = lineLength < 1e-6 ? 0
                        : Math.abs((neighbor.x - source.x) * lineY
                        - (neighbor.y - source.y) * lineX) / lineLength;
                double heuristic = canonicalNeighbor.distanceTo(target) / EDGE;
                open.add(new PathNode(neighborKey, canonicalNeighbor, tentative,
                        tentative + heuristic, deviation));
            }
        }
        return List.of(source);
    }

    private record PathNode(Key key, Vertex vertex, double g, double f, double deviation) {
    }

    private static List<Vertex> reconstruct(Map<Key, Vertex> vertices,
                                            Map<Key, Key> parent, Key targetKey) {
        Deque<Vertex> stack = new ArrayDeque<>();
        Key current = targetKey;
        int guard = 0;
        while (current != null && guard++ < 10_000) {
            Vertex vertex = vertices.get(current);
            if (vertex != null) {
                stack.push(vertex);
            }
            current = parent.get(current);
        }
        return new ArrayList<>(stack);
    }

    /**
     * Canonicalizes floating-point vertex coordinates so the same lattice point
     * always has exactly the same position when it comes from neighbouring cell
     * calculations.
     */
    public static Vertex canonical(Vertex vertex) {
        long x = Math.round(vertex.x * KEY_SCALE);
        long y = Math.round(vertex.y * KEY_SCALE);
        return new Vertex(x / KEY_SCALE, y / KEY_SCALE);
    }

    /** All neighboring vertices to a point, useful for previews and tests. */
    public static List<Vertex> verticesAround(double x, double y, double radius) {
        return verticesIn(new Bounds(x - radius, y - radius, x + radius, y + radius));
    }
}
