package DSA;

import java.util.*;

class NetworkLatencyFloydWarshall {

    public static int[][] floydWarshall(int n, int[][] connections) {
        // Initialize distance matrix
        int[][] dist = new int[n][n];
        
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], Integer.MAX_VALUE / 2); // avoid overflow
            dist[i][i] = 0;
        }

        // Load edges
        for (int[] conn : connections) {
            int u = conn[0], v = conn[1], w = conn[2];
            dist[u][v] = Math.min(dist[u][v], w);
            dist[v][u] = Math.min(dist[v][u], w); // remove if directed
        }

        // Floyd–Warshall DP
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                    }
                }
            }
        }

        return dist;
    }

    public static void main(String[] args) {
        int n = 5; // number of services
        int[][] connections = {
            {0, 1, 200},
            {1, 2, 100},
            {0, 3, 500},
            {3, 4, 50},
            {2, 4, 20}
        };

        // Precompute all-pairs shortest paths
        int[][] dist = floydWarshall(n, connections);

        // Queries
        int[][] queries = {
            {0, 4},
            {1, 3},
            {0, 2}
        };

        for (int[] q : queries) {
            int src = q[0], dest = q[1];
            int latency = dist[src][dest];
            System.out.println("Min latency from " + src + " to " + dest + " = " +
                (latency >= Integer.MAX_VALUE / 2 ? -1 : latency));
        }
    }
}
