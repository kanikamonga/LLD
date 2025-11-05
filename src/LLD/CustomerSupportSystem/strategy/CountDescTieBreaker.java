package LLD.CustomerSupportSystem.strategy;

import LLD.CustomerSupportSystem.model.AgentStat;

import java.util.Comparator;

// Prefer more ratings (higher count)
public class CountDescTieBreaker implements TieBreaker {
    @Override public Comparator<AgentStat> comparator() {
        return Comparator.comparingInt(AgentStat::getCount).reversed();
    }
}

