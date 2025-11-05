package DSA;

import java.util.*;

public class PodSchedulerBestFit {
    public static void main(String[] args) {
        // Given input
        int nodes = 2; // number of nodes
        int pods = 3; // number of pods

        // Node resources
        int[] nodeCpu = {4, 2};
        int[] nodeMem = {8, 4};

        // Pod requirements
        int[] podCpu = {2, 1, 3};
        int[] podMem = {2, 3, 5};

        int[] result = new int[pods];
        Arrays.fill(result, -1);

        for (int i = 0; i < pods; i++) {
            int bestNode = -1;
            int bestWaste = Integer.MAX_VALUE;

            for (int j = 0; j < nodes; j++) {
                if (nodeCpu[j] >= podCpu[i] && nodeMem[j] >= podMem[i]) {
                    int remainingCpu = nodeCpu[j] - podCpu[i];
                    int remainingMem = nodeMem[j] - podMem[i];
                    int waste = remainingCpu + remainingMem;

//                     Tie-breaker: if waste equal, prefer smaller index
                    if (waste < bestWaste || (waste == bestWaste && j < bestNode)) {
                        bestWaste = waste;
                        bestNode = j;
                    }
                }
            }

            if (bestNode != -1) {
                nodeCpu[bestNode] -= podCpu[i];
                nodeMem[bestNode] -= podMem[i];
                result[i] = bestNode;
            }
        }

        // Print results
        for (int res : result) {
            System.out.println(res);
        }
    }
}
