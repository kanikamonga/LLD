package LLD.CustomerSupportSystem.service;

import LLD.CustomerSupportSystem.AgentComparators;
import LLD.CustomerSupportSystem.model.AgentRating;
import LLD.CustomerSupportSystem.model.AgentStat;
import LLD.CustomerSupportSystem.strategy.TieBreaker;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public class RatingService {
    // overall: agent -> (sum,count)
    private final Map<String, AgentRating> overallRatings = new HashMap<>();
    // monthly: YearMonth -> (agent -> (sum,count))
    private final Map<YearMonth, Map<String, AgentRating>> monthlyRatings = new HashMap<>();

    public void addRating(String agent, int rating, YearMonth month) {
	    YearMonth m = YearMonth.of(LocalDate.now().getYear(), LocalDate.now().getMonth());
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be 1..5");
        overallRatings.computeIfAbsent(agent, k -> new AgentRating()).addRating(rating);
        monthlyRatings
                .computeIfAbsent(month, k -> new HashMap<>())
                .computeIfAbsent(agent, k -> new AgentRating())
                .addRating(rating);
    }

    // Overall averages sorted by avg DESC + chain of tie-breakers
    public List<Map.Entry<String, Double>> getOverallAverageRatings(TieBreaker... tieBreakers) {
        List<AgentStat> stats = overallRatings.entrySet().stream()
                .map(e -> new AgentStat(e.getKey(), e.getValue().getTotalSum(), e.getValue().getCount()))
                .collect(Collectors.toList());

        stats.sort(AgentComparators.byAverageDescThen(Arrays.asList(tieBreakers)));

        return stats.stream()
                .map(s -> Map.entry(s.getAgentId(), s.getAverage()))
                .collect(Collectors.toList());
    }

    // Monthly averages for a given month, same multi tie-breaker support
    public List<Map.Entry<String, Double>> getMonthlyAverageRatings(YearMonth month, TieBreaker... tieBreakers) {
        Map<String, AgentRating> map = monthlyRatings.getOrDefault(month, Collections.emptyMap());
        List<AgentStat> stats = map.entrySet().stream()
                .map(e -> new AgentStat(e.getKey(), e.getValue().getTotalSum(), e.getValue().getCount()))
                .collect(Collectors.toList());

        stats.sort(AgentComparators.byAverageDescThen(Arrays.asList(tieBreakers)));

        return stats.stream()
                .map(s -> Map.entry(s.getAgentId(), s.getAverage()))
                .collect(Collectors.toList());
    }

    // Optional helpers if you need them elsewhere
    public Map<String, Double> getOverallAveragesUnsorted() {
        return overallRatings.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAverage()));
    }

    public Map<YearMonth, Map<String, Double>> exportMonthlyAverages() {
        Map<YearMonth, Map<String, Double>> out = new HashMap<>();
        for (var entry : monthlyRatings.entrySet()) {
            out.put(entry.getKey(),
                    entry.getValue().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAverage())));
        }
        return out;
    }
}
