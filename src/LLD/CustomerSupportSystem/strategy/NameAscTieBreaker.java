package LLD.CustomerSupportSystem.strategy;

import LLD.CustomerSupportSystem.model.AgentStat;

import java.util.Comparator;

// Deterministic fallback: alphabetical by agent id
public class NameAscTieBreaker implements TieBreaker {
    @Override public Comparator<AgentStat> comparator() {
        return Comparator.comparing(AgentStat::getAgentId);
    }
}