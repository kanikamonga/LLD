package LLD.popularitycounter;

/** Walks through the basic API so the behaviour is demoable end-to-end. */
public final class PopularityCounterDemo {

    public static void main(String[] args) {
        PopularityCounter counter = new PopularityCounter();

        counter.addUser("alice");
        counter.addUser("bob");
        counter.addUser("carol");
        System.out.println("Added alice, bob, carol (all at 0 votes)");
        System.out.println("  most voted -> " + counter.getUserWithMostVote() + " (tie: earliest added)");

        counter.incrementVote("bob");
        counter.incrementVote("bob");
        System.out.println("bob +2 -> most voted: " + counter.getUserWithMostVote()
                + " (bob=" + counter.getVotes("bob") + ")");

        counter.incrementVote("carol");
        counter.incrementVote("carol");
        counter.incrementVote("carol");
        System.out.println("carol +3 -> most voted: " + counter.getUserWithMostVote()
                + " (carol=" + counter.getVotes("carol") + ")");

        counter.decrementVote("carol");
        counter.decrementVote("carol");
        System.out.println("carol -2 -> most voted: " + counter.getUserWithMostVote()
                + " (bob=" + counter.getVotes("bob") + ", carol=" + counter.getVotes("carol") + ")");

        // Votes may go negative - decrement is a pure mirror of increment, no clamping.
        counter.decrementVote("alice");
        counter.decrementVote("alice");
        System.out.println("alice -2 -> alice=" + counter.getVotes("alice")
                + ", most voted still " + counter.getUserWithMostVote());

        counter.removeUser("bob");
        System.out.println("removed bob -> most voted: " + counter.getUserWithMostVote()
                + " (carol=" + counter.getVotes("carol") + ")");

        counter.removeUser("carol");
        counter.removeUser("alice");
        System.out.println("removed everyone -> userCount=" + counter.userCount());
        try {
            counter.getUserWithMostVote();
        } catch (NoUsersException e) {
            System.out.println("  querying top user now throws: " + e.getMessage());
        }
    }
}
