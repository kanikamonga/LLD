package DSA.Google;

import java.util.ArrayList;
import java.util.List;

/*
 * Problem:
 * A deck contains cards with X suits and Y ranks. A card is represented as
 * (suit, rank), for example S2R5. Find the highest five-card straight flush:
 * five cards of one suit whose ranks are consecutive.
 *
 * A suit can contain a run longer than five cards. In that case, the highest
 * five-card window in the run is selected.
 *
 * Examples:
 *   S0R7, S2R0, S0R1, S2R1, S2R2, S2R3, S2R5, S2R4
 *   Result: S2R1-S2R5
 *
 *   S1R2, S1R0, S1R1, S0R1, S1R3, S0R7, S1R4
 *   Result: S1R0-S1R4
 *
 * Approach:
 * 1. Build a counting-sort-style table indexed by suit and rank. Since ranks
 *    are bounded, no comparison sort is needed.
 * 2. Scan ranks in increasing order for each suit and track the current
 *    consecutive run.
 * 3. Whenever the run reaches five cards, its last five ranks form a
 *    straight flush candidate.
 * 4. Keep the candidate with the greatest ending rank. If ending ranks tie,
 *    use the suit number as a deterministic tie-breaker.
 *
 * Time Complexity: O(X * Y + N), where N is the number of input cards.
 * Space Complexity: O(X * Y).
 */
public class HighestStraightFlush {

    public static class Card {
        public final int suit;
        public final int rank;

        public Card(int suit, int rank) {
            this.suit = suit;
            this.rank = rank;
        }

        @Override
        public String toString() {
            return "S" + suit + "R" + rank;
        }
    }

    public static List<Card> findHighestStraightFlush(
            int suitCount, int rankCount, List<Card> cards) {
        if (suitCount < 1 || rankCount < 1 || cards == null) {
            throw new IllegalArgumentException("Invalid deck");
        }

        // Counting-sort-style presence table: lookup by rank is O(1).
        // A boolean is enough because a valid deck has at most one card for
        // each suit/rank pair; duplicate cards would not change the result.
        boolean[][] present = new boolean[suitCount][rankCount];
        for (Card card : cards) {
            if (card == null || card.suit < 0 || card.suit >= suitCount
                    || card.rank < 0 || card.rank >= rankCount) {
                throw new IllegalArgumentException("Card is outside the deck");
            }
            present[card.suit][card.rank] = true;
        }

        int bestSuit = -1;
        int bestEndRank = -1;

        for (int suit = 0; suit < suitCount; suit++) {
            int consecutive = 0;

            for (int rank = 0; rank < rankCount; rank++) {
                if (present[suit][rank]) {
                    consecutive++;
                } else {
                    consecutive = 0;
                }

                if (consecutive >= 5
                        && (rank > bestEndRank
                        || (rank == bestEndRank && suit > bestSuit))) {
                    bestSuit = suit;
                    bestEndRank = rank;
                }
            }
        }

        if (bestSuit == -1) {
            return new ArrayList<Card>();
        }

        List<Card> result = new ArrayList<Card>(5);
        for (int rank = bestEndRank - 4; rank <= bestEndRank; rank++) {
            result.add(new Card(bestSuit, rank));
        }
        return result;
    }

    public static void main(String[] args) {
        List<Card> cards = new ArrayList<Card>();
        cards.add(new Card(0, 7));
        cards.add(new Card(2, 0));
        cards.add(new Card(0, 1));
        cards.add(new Card(2, 1));
        cards.add(new Card(2, 2));
        cards.add(new Card(2, 3));
        cards.add(new Card(2, 5));
        cards.add(new Card(2, 4));

        System.out.println(findHighestStraightFlush(3, 8, cards));
    }
}
