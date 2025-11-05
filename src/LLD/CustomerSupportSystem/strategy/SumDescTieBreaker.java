package LLD.CustomerSupportSystem.strategy;

import LLD.CustomerSupportSystem.model.AgentStat;

import java.util.Comparator;

// Prefer higher total sum of ratings
public class SumDescTieBreaker implements TieBreaker {
    @Override public Comparator<AgentStat> comparator() {
        return Comparator.comparingInt(AgentStat::getTotalSum).reversed();
    }
}
