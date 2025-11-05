package LLD.CustomerSupportSystem.model;

// Internal sortable view (used for building leaderboards on demand)
public class AgentStat {
    private final String agentId;
    private final int totalSum;
    private final int count;

    public AgentStat(String agentId, int totalSum, int count) {
        this.agentId = agentId;
        this.totalSum = totalSum;
        this.count = count;
    }

    public String getAgentId() { return agentId; }
    public int getTotalSum() { return totalSum; }
    public int getCount() { return count; }
    public double getAverage() { return count == 0 ? 0.0 : (double) totalSum / count; }
}
