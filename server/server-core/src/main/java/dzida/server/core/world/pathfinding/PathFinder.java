package dzida.server.core.world.pathfinding;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dzida.server.core.basic.unit.Graph;
import dzida.server.core.basic.unit.Line;
import dzida.server.core.basic.unit.Point;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pathfinder that use graph with points in line of sight and A* algorithm to find the shortest path.
 * For more information see: http://www.david-gouveia.com/portfolio/pathfinding-on-a-2d-polygonal-map.
 */
public class PathFinder {
    private final CollisionMap collisionMap;

    public PathFinder(CollisionMap collisionMap) {
        this.collisionMap = collisionMap;
    }

    public List<Point> findPathToDestination(Point begin, Point end) {
        Optional<MovableArea> childPolygonOpt = collisionMap.getMovableAreaForPosition(begin);

        if (!childPolygonOpt.isPresent()) {
            // Player can not have be inside collidable polygon.
            return Collections.singletonList(begin);
        }
        MovableArea movableArea = childPolygonOpt.get();

        Point reachableDestination = getEndPoint(begin, end, movableArea);
        if (isInLineOfSight(begin, reachableDestination, movableArea)) {
            return ImmutableList.of(begin, reachableDestination);
        }

        Graph<Point> enrichedLineOfSightGraph = addMoveToGraph(begin, reachableDestination, movableArea);
        return AStar.findShortestPath(begin, reachableDestination, enrichedLineOfSightGraph);
    }

    private Graph<Point> addMoveToGraph(Point begin, Point destination, MovableArea movableArea) {
        Graph<Point> lineOfSightGraph = movableArea.getLineOfSightGraph();
        List<Point> convexPoints = lineOfSightGraph.getAllNodes();

        List<Point> pointsFromStart = findPointsInLineOfSight(begin, convexPoints, movableArea);
        List<Point> pointsToEnd = findPointsInLineOfSight(destination, convexPoints, movableArea);
        Graph.Builder<Point> builder = Graph.builder(lineOfSightGraph);
        builder.put(begin, pointsFromStart);
        pointsToEnd.forEach(point -> builder.put(point, destination));

        return builder.build();
    }

    private List<Point> findPointsInLineOfSight(Point point, List<Point> convexPoints, MovableArea polygon) {
        return convexPoints.stream().filter(p2 -> isInLineOfSight(point, p2, polygon)).collect(Collectors.toList());
    }

    private Point getEndPoint(Point begin, Point end, MovableArea polygon) {
        if (polygon.getPolygon().isInside(end.getX(), end.getY())) {
            return end;
        } else {
            return findClosestReachableEndPoint(begin, end, polygon);
        }
    }

    private Point findClosestReachableEndPoint(Point begin, Point end, MovableArea polygon) {
        Line moveLine = Line.of(begin, end);
        Optional<Point> nearestCollationPointToEnd = Stream.concat(
                polygon.getPolygon().getIntersections(moveLine).stream(),
                polygon.getCollisionBlocks().stream().flatMap(collisionBlock -> collisionBlock.getPolygon().getIntersections(moveLine).stream())
        ).min((o1, o2) -> (int) (o1.distanceSqrTo(end) - o2.distanceSqrTo(end)));

        // assuming that end is outside of movable area so move line will crossed the boarded.
        return nearestCollationPointToEnd.get();
    }

    private boolean isInLineOfSight(Point begin, Point end, MovableArea polygon) {
        return polygon.getPolygon().isLineInside(begin.getX(), begin.getY(), end.getX(), end.getY()) &&
                polygon.getCollisionBlocks().stream().allMatch(child -> child.getPolygon().isLineOutside(begin.getX(), begin.getY(), end.getX(), end.getY()));
    }

    static class CollisionMap {
        private final List<MovableArea> movableAreas;

        CollisionMap(List<MovableArea> movableAreas) {
            this.movableAreas = movableAreas;
        }

        public Optional<MovableArea> getMovableAreaForPosition(Point position) {
            return Optional.of(getMovableAreaForPosition(position, movableAreas));
        }

        private MovableArea getMovableAreaForPosition(Point position, List<MovableArea> movableAreas) {
            MovableArea movableAreaForPosition = movableAreas.stream()
                    .filter(movableArea -> {
                        Polygon polygon = movableArea.getPolygon();
                        return polygon.isInside(position.getX(), position.getY()) || polygon.isOnBorder(position);
                    })
                    .findAny().get();

            Optional<CollisionBlock> blackPolygon = movableAreaForPosition.getCollisionBlocks().stream()
                    .filter(block -> {
                        Polygon polygon = block.getPolygon();
                        return polygon.isInside(position.getX(), position.getY()) && !polygon.isOnBorder(position);
                    })
                    .findAny();

            return blackPolygon.map(p -> getMovableAreaForPosition(position, p.getMovableAreas())).orElse(movableAreaForPosition);
        }
    }

    static class MovableArea {
        private final Polygon polygon;
        private final List<CollisionBlock> childPolygons;
        private final Graph<Point> lineOfSightGraph;

        MovableArea(Polygon polygon, List<CollisionBlock> childPolygons, Graph<Point> lineOfSightGraph) {
            this.polygon = polygon;
            this.childPolygons = childPolygons;
            this.lineOfSightGraph = lineOfSightGraph;
        }

        public Polygon getPolygon() {
            return polygon;
        }

        Graph<Point> getLineOfSightGraph() {
            return lineOfSightGraph;
        }

        List<CollisionBlock> getCollisionBlocks() {
            return childPolygons;
        }
    }

    static class CollisionBlock {
        private final Polygon polygon;
        private final List<MovableArea> childPolygons;

        CollisionBlock(Polygon polygon, List<MovableArea> childPolygons) {
            this.polygon = polygon;
            this.childPolygons = childPolygons;
        }

        public Polygon getPolygon() {
            return polygon;
        }

        List<MovableArea> getMovableAreas() {
            return childPolygons;
        }
    }

}
