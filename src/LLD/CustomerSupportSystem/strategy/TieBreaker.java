package LLD.CustomerSupportSystem.strategy;

import LLD.CustomerSupportSystem.model.AgentStat;

import java.util.Comparator;

public interface TieBreaker {
    Comparator<AgentStat> comparator();
}
