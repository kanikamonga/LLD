package LLD.CustomerSupportSystem.model;

public class AgentRating {
    private int totalSum;
    private int count;

    public void addRating(int rating) {
        totalSum += rating;
        count++;
    }

    public double getAverage() {
        return count == 0 ? 0.0 : (double) totalSum / count;
    }

    public int getTotalSum() {
        return totalSum;
    }

    public int getCount() {
        return count;
    }
}
