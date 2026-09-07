package LLD.Marathon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * LLD skeleton for finding the fastest runner from a paginated external API.
 *
 * HTTP, Spring Retry, and the concrete rate limiter are intentionally left
 * behind interfaces. They can be supplied independently in production.
 */
public final class MarathonFastestRunnerDesign {

    public static final class Runner {
        private final String id;
        private final String name;
        private final Duration finishTime;

        public Runner(String id, String name, Duration finishTime) {
            if (id == null || name == null || finishTime == null || finishTime.isNegative()) {
                throw new IllegalArgumentException("Invalid runner");
            }
            this.id = id;
            this.name = name;
            this.finishTime = finishTime;
        }

        public String id() { return id; }
        public String name() { return name; }
        public Duration finishTime() { return finishTime; }
    }

    public static final class RunnerApiResponse {
        private final List<Runner> runners;
        private final int pageNumber;
        private final int totalPages;
        private final String snapshotId;

        public RunnerApiResponse(List<Runner> runners, int pageNumber,
                                 int totalPages, String snapshotId) {
            this.runners = Collections.unmodifiableList(new ArrayList<>(runners));
            this.pageNumber = pageNumber;
            this.totalPages = totalPages;
            this.snapshotId = snapshotId;
        }

        public List<Runner> runners() { return runners; }
        public int pageNumber() { return pageNumber; }
        public int totalPages() { return totalPages; }
        public String snapshotId() { return snapshotId; }
    }

    public static final class FastestRunnersResult {
        private final List<Runner> runners;

        public FastestRunnersResult(List<Runner> runners) {
            this.runners = Collections.unmodifiableList(new ArrayList<>(runners));
        }

        public List<Runner> runners() { return runners; }
    }

    public interface RunnerApiClient {
        /*
         * The implementation should apply rate limiting and bounded retries.
         * snapshotId must be reused for every page in one operation.
         */
        RunnerApiResponse fetchPage(int pageNumber, String snapshotId);
    }

    public interface RateLimiter {
        void acquire() throws InterruptedException;
    }

    public interface FastestRunnerAggregator {
        void accept(List<Runner> runners);

        FastestRunnersResult result();
    }

    /**
     * The client implementation can be composed as:
     *
     * RateLimitedClient(RetryingClient(HttpClient), sharedRateLimiter)
     *
     * Retry transient failures (timeouts, 5xx, 429), use exponential jitter,
     * honor Retry-After, and propagate a failure after the operation deadline.
     */
    public static final class RunnerApiService {
        private final RunnerApiClient client;
        private final int poolSize;

        public RunnerApiService(RunnerApiClient client, int poolSize) {
            if (client == null || poolSize < 1) {
                throw new IllegalArgumentException("Invalid service configuration");
            }
            this.client = client;
            this.poolSize = poolSize;
        }

        public FastestRunnersResult getFastestRunners() {
            RunnerApiResponse firstPage = client.fetchPage(1, null);
            validate(firstPage, 1, null);

            FastestRunnerAggregator aggregator = new MinRunnerAggregator();
            aggregator.accept(firstPage.runners());

            if (firstPage.totalPages() <= 1) {
                return aggregator.result();
            }

            ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            List<Future<RunnerApiResponse>> pages = new ArrayList<>();
            try {
                for (int page = 2; page <= firstPage.totalPages(); page++) {
                    int pageNumber = page;
                    pages.add(executor.submit(() ->
                            client.fetchPage(pageNumber, firstPage.snapshotId())));
                }

                for (int index = 0; index < pages.size(); index++) {
                    RunnerApiResponse response = pages.get(index).get();
                    validate(response, index + 2, firstPage.snapshotId());
                    aggregator.accept(response.runners());
                }
                return aggregator.result();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                cancelAll(pages);
                throw new MarathonFetchException("Marathon scan interrupted", exception);
            } catch (ExecutionException exception) {
                cancelAll(pages);
                throw new MarathonFetchException("Unable to fetch every marathon page",
                        exception.getCause());
            } finally {
                executor.shutdownNow();
            }
        }

        private void validate(RunnerApiResponse response, int expectedPage, String expectedSnapshot) {
            if (response == null
                    || response.pageNumber() != expectedPage
                    || response.totalPages() < expectedPage
                    || (expectedSnapshot != null && !expectedSnapshot.equals(response.snapshotId()))) {
                throw new MarathonFetchException("Invalid or inconsistent API response", null);
            }
        }

        private void cancelAll(List<? extends Future<?>> pages) {
            pages.forEach(page -> page.cancel(true));
        }
    }

    private static final class MinRunnerAggregator implements FastestRunnerAggregator {
        private Duration fastestTime;
        private final List<Runner> fastestRunners = new ArrayList<>();

        @Override
        public void accept(List<Runner> runners) {
            if (runners == null) {
                throw new IllegalArgumentException("Runners cannot be null");
            }
            for (Runner runner : runners) {
                if (fastestTime == null || runner.finishTime().compareTo(fastestTime) < 0) {
                    fastestTime = runner.finishTime();
                    fastestRunners.clear();
                    fastestRunners.add(runner);
                } else if (runner.finishTime().equals(fastestTime)) {
                    fastestRunners.add(runner);
                }
            }
        }

        @Override
        public FastestRunnersResult result() {
            return new FastestRunnersResult(Collections.unmodifiableList(fastestRunners));
        }
    }

    public static final class MarathonFetchException extends RuntimeException {
        public MarathonFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private MarathonFastestRunnerDesign() {
    }
}
