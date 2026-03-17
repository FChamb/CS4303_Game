import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * A* pathfinding over the tile grid.
 *
 * This class handles the Level 4 pathfinding requirement by converting between:
 * - the continuous world space used by the game
 * - the discrete tile grid used for pathfinding
 *
 * Enemy and player positions are converted into tile coordinates, A* is run
 * over the traversable grid, and the resulting path is turned back into
 * world space waypoints for the enemies to follow.
 */
public class PathFinder {

    private static class Node {
        int r, c;
        float g;
        float h;
        Node parent;

        Node(int r, int c, float g, float h, Node parent) {
            this.r = r;
            this.c = c;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        float f() {
            return g + h;
        }
    }

    // 8-directional movement
    private static final int[][] DIRS = {
            {-1,  0}, {1,  0}, {0, -1}, {0,  1},
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };

    /**
     * Finds a path from start tile to goal tile.
     *
     * Returns a list of tile coordinates [row, col] describing the path.
     * If no path is found, returns an empty list.
     */
    public ArrayList<int[]> findPath(TileMap map, int startR, int startC, int goalR, int goalC, int maxNodes) {
        ArrayList<int[]> empty = new ArrayList<>();

        if (!map.inBounds(startR, startC) || !map.inBounds(goalR, goalC)) return empty;
        if (!isWalkable(map, startR, startC) || !isWalkable(map, goalR, goalC)) return empty;

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.f(), b.f()));
        boolean[][] closed = new boolean[map.rows][map.cols];

        float[][] bestG = new float[map.rows][map.cols];
        for (int r = 0; r < map.rows; r++) {
            Arrays.fill(bestG[r], Float.POSITIVE_INFINITY);
        }

        Node start = new Node(startR, startC, 0f, heuristic(startR, startC, goalR, goalC), null);
        open.add(start);
        bestG[startR][startC] = 0f;

        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            Node current = open.poll();

            if (closed[current.r][current.c]) continue;
            closed[current.r][current.c] = true;
            expanded++;

            if (current.r == goalR && current.c == goalC) {
                return reconstructPath(current);
            }

            for (int[] d : DIRS) {
                int nr = current.r + d[0];
                int nc = current.c + d[1];

                if (!map.inBounds(nr, nc)) continue;
                if (!isWalkable(map, nr, nc)) continue;
                if (closed[nr][nc]) continue;

                // 8-directional movement cost
                float stepCost = (d[0] != 0 && d[1] != 0) ? 1.4142135f : 1.0f;
                float newG = current.g + stepCost;

                if (newG < bestG[nr][nc]) {
                    bestG[nr][nc] = newG;
                    float h = heuristic(nr, nc, goalR, goalC);
                    open.add(new Node(nr, nc, newG, h, current));
                }
            }
        }

        return empty;
    }

    /**
     * For flying enemies, only AIR tiles are traversable.
     */
    private boolean isWalkable(TileMap map, int r, int c) {
        return map.getTile(r, c) == TileTypes.AIR;
    }

    /**
     * Euclidean heuristic works well for 8-directional motion.
     */
    private float heuristic(int r1, int c1, int r2, int c2) {
        float dr = r2 - r1;
        float dc = c2 - c1;
        return (float) Math.sqrt(dr * dr + dc * dc);
    }

    private ArrayList<int[]> reconstructPath(Node goal) {
        ArrayList<int[]> path = new ArrayList<>();
        Node cur = goal;
        while (cur != null) {
            path.add(0, new int[]{cur.r, cur.c});
            cur = cur.parent;
        }
        return path;
    }
}