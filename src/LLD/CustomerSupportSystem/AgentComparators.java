package LLD.CustomerSupportSystem;

import LLD.CustomerSupportSystem.model.AgentStat;
import LLD.CustomerSupportSystem.strategy.TieBreaker;

import java.util.*;

public final class AgentComparators {
    private AgentComparators() {}

    // Primary compare by average DESC using exact cross-multiplication
    private static int compareAverageDesc(AgentStat a, AgentStat b) {
        long left  = (long) a.getTotalSum() * (long) b.getCount();
        long right = (long) b.getTotalSum() * (long) a.getCount();
        // right-left because we want DESC by avg
        return Long.compare(right, left);
    }

    public static Comparator<AgentStat> byAverageDescThen(List<TieBreaker> tieBreakers) {
        return (a, b) -> {
            int cmp = compareAverageDesc(a, b);
            if (cmp != 0) return cmp;
            // apply tie-breakers in the given order
            for (TieBreaker tb : tieBreakers) {
                int t = tb.comparator().compare(a, b);
                if (t != 0) return t;
            }
            // final deterministic fallback
            return a.getAgentId().compareTo(b.getAgentId());
        };
    }
}
