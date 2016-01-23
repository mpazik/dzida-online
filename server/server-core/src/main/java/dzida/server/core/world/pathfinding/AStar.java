package dzida.server.core.world.pathfinding;

import dzida.server.core.basic.unit.Graph;
import dzida.server.core.basic.unit.Point;

import java.util.*;

public class AStar {
    private final int startId;
    private final Point end;
    private final Graph<Point> graph;
    private final int[] cameFrom;

    private AStar(int startId, Point end, Graph<Point> graph) {
        this.startId = startId;
        this.graph = graph;
        this.end = end;
        cameFrom = new int[graph.size()];
    }

    public static List<Point> findShortestPath(Point start, Point end, Graph<Point> graph) {
        return new AStar(graph.getNodeId(start), end, graph).findShortestPath();
    }

    private List<Point> findShortestPath() {
        Set<Integer> nodesEvaluated = new HashSet<>();

        double[] distanceFromStart = new double[graph.size()]; // Shortest distances to the start point from the given points.
        double[] distanceToEnd = new double[graph.size()]; // Shortest heuristic distances to the end from the given points.

        Comparator<Integer> heuristicDistanceToEndComparator = (p1, p2) -> (int)(distanceToEnd[p1] - distanceToEnd[p2]);
        PriorityQueue<Integer> nodesToEvaluate = new PriorityQueue<>(heuristicDistanceToEndComparator);


        distanceFromStart[startId] = 0.0;
        distanceToEnd[startId] = getStraitDistanceToEnd(startId);
        nodesToEvaluate.add(startId);


        while (!nodesToEvaluate.isEmpty()) {
            int currentId = nodesToEvaluate.poll();

            Point currentPoint = graph.getElement(currentId);
            if (currentPoint == end) {
                return reconstructPath(currentId);
            }
            nodesEvaluated.add(currentId);

            for (int neighborId : graph.getNodeIds(currentId)) {
                if (nodesEvaluated.contains(neighborId)) continue;

                Point neighborPoint = graph.getElement(neighborId);
                double distanceBetweenTwoNodes = currentPoint.distanceTo(neighborPoint);
                double lengthOfCurrentPath = distanceFromStart[currentId] + distanceBetweenTwoNodes;

                if (distanceFromStart[neighborId] == 0 || lengthOfCurrentPath < distanceFromStart[neighborId]) {
                    distanceFromStart[neighborId] = lengthOfCurrentPath;
                    distanceToEnd[neighborId] = lengthOfCurrentPath + getStraitDistanceToEnd(neighborId);

                    cameFrom[neighborId] = currentId;
                    if (nodesToEvaluate.contains(neighborId)) {
                        // update priority of the neighbor
                        nodesToEvaluate.remove(neighborId);
                    }
                    nodesToEvaluate.add(neighborId);
                }
            }
        }

        Comparator<Integer> straitDistanceToEndComparator = (p1, p2) -> Math.toIntExact(Math.round(getStraitDistanceToEnd(p1) - getStraitDistanceToEnd(p2)));
        int closestEnd = nodesEvaluated.stream().sorted(straitDistanceToEndComparator).findFirst().orElse(startId);
        return reconstructPath(closestEnd);
    }

    private double getStraitDistanceToEnd(int id) {
        return graph.getElement(id).distanceTo(end);
    }

    private List<Point> reconstructPath(int destinationId) {
        final List<Point> pathList = new ArrayList<>();
        while (destinationId != startId) {
            pathList.add(graph.getElement(destinationId));
            destinationId = cameFrom[destinationId];
        }
        pathList.add(graph.getElement(startId));
        Collections.reverse(pathList);
        return pathList;
    }
}
