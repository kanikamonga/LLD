package LLD.coffeemachine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free functional integration suite; concurrency coordination uses bounded latches. */
public final class CoffeeMachineTest {
    private static int passed;
    private static int failed;

    @FunctionalInterface
    private interface Test { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        run("variable recipes and repeated orders", CoffeeMachineTest::variableRecipes);
        run("failed reservations never partially consume", CoffeeMachineTest::atomicFailures);
        run("missing precedes insufficient in either order", CoffeeMachineTest::missingPrecedence);
        run("inclusive low indicators, refill and snapshots", CoffeeMachineTest::indicatorsAndRefill);
        run("refill introduces missing ingredient", CoffeeMachineTest::newIngredient);
        run("N preparations overlap and queued requests respect N", CoffeeMachineTest::parallelOutlets);
        run("concurrent resource contention is atomic", CoffeeMachineTest::contention);
        run("interruption rolls back and releases outlet", CoffeeMachineTest::interruption);
        run("queued cancellation consumes nothing", CoffeeMachineTest::queuedCancellation);
        run("hook failure propagates and rolls back", CoffeeMachineTest::hookFailure);
        run("interrupt flag and hook errors also roll back", CoffeeMachineTest::otherPreparationFailures);
        run("refill cannot overflow rollback capacity", CoffeeMachineTest::refillDuringReservation);
        run("graceful close drains queue and rejects mutations", CoffeeMachineTest::lifecycle);
        run("configuration defensively copies all maps", CoffeeMachineTest::immutableConfiguration);
        run("API validates invalid values", CoffeeMachineTest::validation);
        run("strict JSON parses escapes and maximum quantities", CoffeeMachineTest::jsonValid);
        run("strict JSON rejects malformed and unsupported input", CoffeeMachineTest::jsonInvalid);
        run("sample outcomes conserve inventory under scheduling", CoffeeMachineTest::sampleOutcomes);
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed != 0) throw new AssertionError("Coffee machine integration suite failed");
    }

    private static void run(String name, Test test) {
        try {
            test.run();
            passed++;
            System.out.println("PASS - " + name);
        } catch (Throwable failure) {
            failed++;
            System.err.println("FAIL - " + name);
            failure.printStackTrace();
        }
    }

    private static Map<String, Long> quantities(Object... pairs) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            result.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
        return result;
    }

    private static MachineConfig config(int outlets, Map<String, Long> inventory,
                                        Map<String, Long> recipe) {
        return new MachineConfig(outlets, inventory, Collections.singletonMap("drink", recipe));
    }

    private static CoffeeMachine machine(int outlets, Map<String, Long> inventory,
                                         Map<String, Long> recipe) {
        return new CoffeeMachine(config(outlets, inventory, recipe), 0, 2);
    }

    private static CoffeeMachine.Result get(Future<CoffeeMachine.Result> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        check(latch.await(5, TimeUnit.SECONDS), "latch timed out");
    }

    private static void stop(CoffeeMachine machine) throws InterruptedException {
        machine.close();
        check(machine.awaitTermination(5, TimeUnit.SECONDS), "workers did not terminate");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual) {
        check(expected.equals(actual), "expected " + expected + ", got " + actual);
    }

    private static <T extends Throwable> T expect(Class<T> type, Test action) throws Exception {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return type.cast(failure);
            throw new AssertionError("expected " + type.getSimpleName() + ", got " + failure, failure);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static void variableRecipes() throws Exception {
        Map<String, Map<String, Long>> recipes = new LinkedHashMap<>();
        recipes.put("tea", quantities("water", 2, "milk", 1));
        recipes.put("coffee", quantities("water", 1, "milk", 4));
        CoffeeMachine machine = new CoffeeMachine(new MachineConfig(2,
                quantities("water", 10, "milk", 10), recipes), 0, 0);
        try {
            equal(CoffeeMachine.Status.PREPARED, get(machine.order("tea")).status());
            equal(CoffeeMachine.Status.PREPARED, get(machine.order("coffee")).status());
            equal(CoffeeMachine.Status.PREPARED, get(machine.order("coffee")).status());
            equal(CoffeeMachine.Status.INSUFFICIENT, get(machine.order("coffee")).status());
            equal(quantities("water", 6, "milk", 1), machine.inventory());
            equal("tea is prepared", get(machine.order("tea")).message());
            equal(CoffeeMachine.Status.UNKNOWN_BEVERAGE, get(machine.order("espresso")).status());
        } finally { stop(machine); }
    }

    private static void atomicFailures() throws Exception {
        CoffeeMachine machine = machine(2, quantities("water", 10, "milk", 1),
                quantities("water", 2, "milk", 2));
        try {
            for (int i = 0; i < 10; i++) {
                equal(CoffeeMachine.Status.INSUFFICIENT, get(machine.order("drink")).status());
                equal(quantities("water", 10, "milk", 1), machine.inventory());
            }
            equal("drink cannot be prepared because item milk is not sufficient",
                    get(machine.order("drink")).message());
        } finally { stop(machine); }
    }

    private static void missingPrecedence() throws Exception {
        for (Map<String, Long> recipe : java.util.Arrays.asList(
                quantities("water", 2, "green_mixture", 1),
                quantities("green_mixture", 1, "water", 2))) {
            CoffeeMachine machine = machine(1, quantities("water", 0), recipe);
            try {
                equal(CoffeeMachine.Status.UNAVAILABLE, get(machine.order("drink")).status());
                equal("drink cannot be prepared because green_mixture is not available",
                        get(machine.order("drink")).message());
                equal(quantities("water", 0), machine.inventory());
            } finally { stop(machine); }
        }
    }

    private static void indicatorsAndRefill() throws Exception {
        CoffeeMachine machine = machine(1, quantities("water", 3, "milk", 2, "sugar", 0),
                quantities("water", 1));
        try {
            equal(quantities("milk", 2, "sugar", 0), machine.lowIngredients());
            Map<String, Long> snapshot = machine.inventory();
            expect(UnsupportedOperationException.class, () -> snapshot.put("water", 1L));
            expect(UnsupportedOperationException.class, () -> machine.lowIngredients().clear());
            get(machine.order("drink"));
            equal(3L, snapshot.get("water"));
            equal(2L, machine.inventory().get("water"));
            equal(quantities("water", 2, "milk", 2, "sugar", 0), machine.lowIngredients());
            machine.refill("water", 1);
            equal(3L, snapshot.get("water"));
            equal(quantities("milk", 2, "sugar", 0), machine.lowIngredients());
            machine.refill("milk", 4);
            equal(quantities("sugar", 0), machine.lowIngredients());
        } finally { stop(machine); }
    }

    private static void newIngredient() throws Exception {
        CoffeeMachine machine = machine(1, quantities("water", 3),
                quantities("water", 1, "green", 2));
        try {
            equal(quantities("green", 0), machine.lowIngredients());
            equal(CoffeeMachine.Status.UNAVAILABLE, get(machine.order("drink")).status());
            machine.refill("green", 2);
            equal(CoffeeMachine.Status.PREPARED, get(machine.order("drink")).status());
            equal(quantities("water", 2, "green", 0), machine.inventory());
            equal(CoffeeMachine.Status.INSUFFICIENT, get(machine.order("drink")).status());
        } finally { stop(machine); }
    }

    private static void parallelOutlets() throws Exception {
        CountDownLatch entered = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        CoffeeMachine machine = new CoffeeMachine(config(3, quantities("water", 12),
                quantities("water", 1)), 37, 0, (name, duration) -> {
            equal(37L, duration);
            equal("drink", name);
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            calls.incrementAndGet();
            entered.countDown();
            try { await(release); }
            finally { active.decrementAndGet(); }
        });
        List<Future<CoffeeMachine.Result>> orders = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) orders.add(machine.order("drink"));
            await(entered);
            equal(3, calls.get());
            equal(3, active.get());
            equal(9L, machine.inventory().get("water"));
            machine.refill("water", 1); // Must not block while the preparation hook is blocked.
            equal(10L, machine.inventory().get("water"));
            release.countDown();
            for (Future<CoffeeMachine.Result> order : orders)
                equal(CoffeeMachine.Status.PREPARED, get(order).status());
            equal(3, maximum.get());
            equal(12, calls.get());
            equal(1L, machine.inventory().get("water"));
        } finally { release.countDown(); stop(machine); }
    }

    private static void contention() throws Exception {
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        CoffeeMachine machine = new CoffeeMachine(config(4, quantities("water", 20, "milk", 10),
                quantities("water", 2, "milk", 1)), 0, 0, (name, duration) -> {
            entered.countDown();
            await(release);
        });
        try {
            List<Future<CoffeeMachine.Result>> orders = new ArrayList<>();
            for (int i = 0; i < 80; i++) orders.add(machine.order("drink"));
            await(entered);
            equal(quantities("water", 12, "milk", 6), machine.inventory());
            release.countDown();
            int successes = 0;
            for (Future<CoffeeMachine.Result> order : orders) {
                CoffeeMachine.Status status = get(order).status();
                if (status == CoffeeMachine.Status.PREPARED) successes++;
                else equal(CoffeeMachine.Status.INSUFFICIENT, status);
            }
            equal(10, successes);
            equal(quantities("water", 0, "milk", 0), machine.inventory());
        } finally { release.countDown(); stop(machine); }
    }

    private static void interruption() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        CoffeeMachine machine = new CoffeeMachine(config(1, quantities("water", 5, "milk", 5),
                quantities("water", 2, "milk", 3)), 0, 0, (name, duration) -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try { await(release); }
                catch (InterruptedException failure) { interrupted.countDown(); throw failure; }
            }
        });
        try {
            Future<CoffeeMachine.Result> first = machine.order("drink");
            await(entered);
            equal(quantities("water", 3, "milk", 2), machine.inventory());
            machine.refill("water", 4);
            check(first.cancel(true), "running order should cancel");
            await(interrupted);
            expect(CancellationException.class, () -> get(first));
            // Same worker executes this only after the cancelled order's rollback.
            equal(CoffeeMachine.Status.PREPARED, get(machine.order("drink")).status());
            equal(quantities("water", 7, "milk", 2), machine.inventory());
        } finally { release.countDown(); stop(machine); }
    }

    private static void queuedCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        CoffeeMachine machine = new CoffeeMachine(config(1, quantities("water", 2),
                quantities("water", 1)), 0, 0, (name, duration) -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
        });
        try {
            Future<CoffeeMachine.Result> first = machine.order("drink");
            await(entered);
            Future<CoffeeMachine.Result> queued = machine.order("drink");
            check(queued.cancel(true), "queued order should cancel");
            release.countDown();
            get(first);
            machine.close();
            check(machine.awaitTermination(5, TimeUnit.SECONDS), "queue should drain");
            equal(1, calls.get());
            equal(1L, machine.inventory().get("water"));
        } finally { release.countDown(); stop(machine); }
    }

    private static void hookFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CoffeeMachine machine = new CoffeeMachine(config(1, quantities("water", 2, "milk", 3),
                quantities("water", 2, "milk", 3)), 0, 0, (name, duration) -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("heater failed");
        });
        try {
            ExecutionException failure = expect(ExecutionException.class, () -> get(machine.order("drink")));
            equal("heater failed", failure.getCause().getMessage());
            equal(quantities("water", 2, "milk", 3), machine.inventory());
            equal(CoffeeMachine.Status.PREPARED, get(machine.order("drink")).status());
        } finally { stop(machine); }
    }

    private static void otherPreparationFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CoffeeMachine machine = new CoffeeMachine(config(1, quantities("water", 2),
                quantities("water", 2)), 0, 0, (name, duration) -> {
            int call = calls.incrementAndGet();
            if (call == 1) Thread.currentThread().interrupt();
            if (call == 2) throw new AssertionError("equipment failure");
        });
        try {
            ExecutionException interrupted = expect(ExecutionException.class,
                    () -> get(machine.order("drink")));
            check(interrupted.getCause() instanceof InterruptedException, "interrupt must be surfaced");
            equal(quantities("water", 2), machine.inventory());
            ExecutionException error = expect(ExecutionException.class,
                    () -> get(machine.order("drink")));
            check(error.getCause() instanceof AssertionError, "hook error must be surfaced");
            equal(quantities("water", 2), machine.inventory());
            equal(CoffeeMachine.Status.PREPARED, get(machine.order("drink")).status());
            equal(quantities("water", 0), machine.inventory());
        } finally { stop(machine); }
    }

    private static void refillDuringReservation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CoffeeMachine machine = new CoffeeMachine(config(1, quantities("water", Long.MAX_VALUE),
                quantities("water", Long.MAX_VALUE)), 0, 0, (name, duration) -> {
            entered.countDown();
            await(release);
            throw new IllegalStateException("rollback");
        });
        try {
            Future<CoffeeMachine.Result> order = machine.order("drink");
            await(entered);
            equal(0L, machine.inventory().get("water"));
            expect(ArithmeticException.class, () -> machine.refill("water", 1));
            equal(0L, machine.inventory().get("water"));
            release.countDown();
            expect(ExecutionException.class, () -> get(order));
            equal(Long.MAX_VALUE, machine.inventory().get("water"));
            expect(ArithmeticException.class, () -> machine.refill("water", Long.MAX_VALUE));
            equal(Long.MAX_VALUE, machine.inventory().get("water"));
        } finally { release.countDown(); stop(machine); }
    }

    private static void lifecycle() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CoffeeMachine machine = new CoffeeMachine(config(1, quantities("water", 2),
                quantities("water", 1)), 0, 0, (name, duration) -> {
            entered.countDown();
            await(release);
        });
        try {
            Future<CoffeeMachine.Result> first = machine.order("drink");
            await(entered);
            Future<CoffeeMachine.Result> second = machine.order("drink");
            machine.close();
            machine.close();
            expect(IllegalStateException.class, () -> machine.order("drink"));
            expect(IllegalStateException.class, () -> machine.refill("water", 1));
            check(!machine.awaitTermination(0, TimeUnit.SECONDS), "blocked order must still run");
            release.countDown();
            equal(CoffeeMachine.Status.PREPARED, get(first).status());
            equal(CoffeeMachine.Status.PREPARED, get(second).status());
            stop(machine);
            equal(quantities("water", 0), machine.inventory());
        } finally { release.countDown(); stop(machine); }
    }

    private static void immutableConfiguration() throws Exception {
        Map<String, Long> stock = quantities("water", 2);
        Map<String, Long> recipe = quantities("water", 1);
        Map<String, Map<String, Long>> recipes = new LinkedHashMap<>();
        recipes.put("drink", recipe);
        MachineConfig config = new MachineConfig(1, stock, recipes);
        stock.clear();
        recipe.put("water", 10L);
        recipes.clear();
        expect(UnsupportedOperationException.class, () -> config.inventory().clear());
        expect(UnsupportedOperationException.class, () -> config.beverages().clear());
        expect(UnsupportedOperationException.class, () -> config.beverages().get("drink").clear());
        CoffeeMachine first = new CoffeeMachine(config, 0, 0);
        CoffeeMachine second = new CoffeeMachine(config, 0, 0);
        try {
            equal(CoffeeMachine.Status.PREPARED, get(first.order("drink")).status());
            equal(2L, second.inventory().get("water"));
            equal(2L, config.inventory().get("water"));
        } finally { stop(first); stop(second); }
    }

    private static void validation() throws Exception {
        Map<String, Long> stock = quantities("water", 1);
        Map<String, Long> recipe = quantities("water", 1);
        expect(IllegalArgumentException.class, () -> config(0, stock, recipe));
        expect(IllegalArgumentException.class, () -> config(-1, stock, recipe));
        expect(IllegalArgumentException.class, () -> config(1, quantities("water", -1), recipe));
        expect(IllegalArgumentException.class, () -> config(1, stock, quantities("water", 0)));
        expect(IllegalArgumentException.class, () -> config(1, stock, quantities("water", -1)));
        expect(IllegalArgumentException.class, () -> config(1, stock, quantities(" ", 1)));
        expect(IllegalArgumentException.class, () -> config(1, stock, Collections.emptyMap()));
        expect(IllegalArgumentException.class, () -> new MachineConfig(1, stock, Collections.emptyMap()));
        Map<String, Long> nullQuantity = new LinkedHashMap<>();
        nullQuantity.put("water", null);
        expect(IllegalArgumentException.class, () -> config(1, nullQuantity, recipe));
        expect(IllegalArgumentException.class, () -> new CoffeeMachine(config(1, stock, recipe), -1, 0));
        expect(IllegalArgumentException.class, () -> new CoffeeMachine(config(1, stock, recipe), 0, -1));
        expect(NullPointerException.class, () -> new CoffeeMachine(config(1, stock, recipe), 0, 0, null));
        CoffeeMachine machine = machine(1, stock, recipe);
        try {
            expect(IllegalArgumentException.class, () -> machine.order(null));
            expect(IllegalArgumentException.class, () -> machine.order(" "));
            expect(IllegalArgumentException.class, () -> machine.refill("water", 0));
            expect(IllegalArgumentException.class, () -> machine.refill("water", -1));
            expect(IllegalArgumentException.class, () -> machine.refill("", 1));
            equal(stock, machine.inventory());
        } finally { stop(machine); }
    }

    private static String minimalJson() {
        return "{\"machine\":{\"outlets\":{\"count_n\":1},\"total_items_quantity\":{\"water\":2},"
                + "\"beverages\":{\"drink\":{\"water\":1}}}}";
    }

    private static void jsonValid() throws Exception {
        String json = " \n\t" + minimalJson().replace("\"water\":2", "\"wa\\u0074er\":" + Long.MAX_VALUE)
                .replace("\"drink\"", "\"d\\\"r\\\\i\\/n\\tk\\n\\r\\b\\f\"") + "\r ";
        MachineConfig parsed = ConfigJson.parse(json);
        equal(1, parsed.outlets());
        equal(Long.MAX_VALUE, parsed.inventory().get("water"));
        String name = "d\"r\\i/n\tk\n\r\b\f";
        check(parsed.beverages().containsKey(name), "string escapes should decode");
        CoffeeMachine machine = new CoffeeMachine(parsed, 0, 0);
        try {
            equal(CoffeeMachine.Status.PREPARED, get(machine.order(name)).status());
            equal(Long.MAX_VALUE - 1, machine.inventory().get("water"));
        } finally { stop(machine); }
        MachineConfig emptyStock = ConfigJson.parse(minimalJson().replace("{\"water\":2}", "{}"));
        equal(Collections.emptyMap(), emptyStock.inventory());
    }

    private static void jsonInvalid() throws Exception {
        String valid = minimalJson();
        List<String> invalid = java.util.Arrays.asList(
                "", "{}", "[]", "true", "null", "-1", "\"root\"", valid + "{}",
                valid.substring(0, valid.length() - 1), valid.replace("2}", "2,}"),
                valid.replace("\"water\":2", "\"water\":2,\"water\":3"),
                valid.replace("\"water\":2", "\"water\":2,\"wa\\u0074er\":3"),
                valid.replace("\"water\":2", "\"water\":9223372036854775808"),
                valid.replace("\"water\":2", "\"water\":-2"),
                valid.replace("\"water\":2", "\"water\":2.0"),
                valid.replace("\"water\":2", "\"water\":2e1"),
                valid.replace("\"water\":2", "\"water\":02"),
                valid.replace("\"water\":2", "\"water\":true"),
                valid.replace("\"water\":2", "\"water\":null"),
                valid.replace("\"water\":2", "\"water\":[]"),
                valid.replace("\"water\":2", "\"water\":\"2\""),
                valid.replace("\"water\":1", "\"water\":0"),
                valid.replace("\"count_n\":1", "\"count_n\":0"),
                valid.replace("\"count_n\":1", "\"count_n\":2147483648"),
                valid.replace("\"outlets\"", "\"unknown\""),
                valid.replace("\"count_n\":1", "\"count_n\":1,\"extra\":1"),
                valid.replace("\"outlets\":{\"count_n\":1}", "\"outlets\":1"),
                valid.replace("{\"drink\":{\"water\":1}}", "{}"),
                valid.replace("\"drink\"", "\" \""),
                valid.replace("\"water\":2", "\"water\":2/*comment*/"),
                valid.replace("\"water\":2", "\"wat\ner\":2"),
                valid.replace("\"water\":2", "\"wat\\xer\":2"),
                valid.replace("\"water\":2", "\"wat\\uZZZZer\":2"),
                "{\"machine\":\"unfinished", "{\"machine\":\"\\u12", "{\"machine\":\"\\",
                "{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{}}}}}}}}}}");
        for (String input : invalid) {
            IllegalArgumentException error = expect(IllegalArgumentException.class, () -> ConfigJson.parse(input));
            check(error.getMessage() != null && !error.getMessage().isEmpty(), "descriptive JSON error");
        }
        check(expect(IllegalArgumentException.class, () -> ConfigJson.parse(
                valid.replace("2}", "2,}"))).getMessage().contains("trailing commas"), "trailing comma reason");
        check(expect(IllegalArgumentException.class, () -> ConfigJson.parse(
                valid.replace("\"water\":2", "\"water\":2,\"water\":3")))
                .getMessage().contains("duplicate key"), "duplicate key reason");
    }

    private static void sampleOutcomes() throws Exception {
        MachineConfig config = ConfigJson.parse(new String(Files.readAllBytes(
                Paths.get("src/LLD/coffeemachine/sample.json")), StandardCharsets.UTF_8));
        equal(4, config.outlets());
        for (int attempt = 0; attempt < 50; attempt++) {
            CoffeeMachine machine = new CoffeeMachine(config, 0, 50);
            try {
                List<String> names = new ArrayList<>(config.beverages().keySet());
                Collections.rotate(names, attempt % names.size());
                Map<String, Future<CoffeeMachine.Result>> orders = new LinkedHashMap<>();
                for (String name : names) orders.put(name, machine.order(name));
                Map<String, Long> remaining = new LinkedHashMap<>(config.inventory());
                int prepared = 0;
                for (Map.Entry<String, Future<CoffeeMachine.Result>> order : orders.entrySet()) {
                    CoffeeMachine.Result result = get(order.getValue());
                    if (order.getKey().equals("green_tea")) {
                        equal(CoffeeMachine.Status.UNAVAILABLE, result.status());
                        check(result.message().contains("green_mixture is not available"), "green mixture reason");
                    } else if (result.status() == CoffeeMachine.Status.PREPARED) {
                        prepared++;
                        config.beverages().get(order.getKey()).forEach((name, amount) ->
                                remaining.put(name, remaining.get(name) - amount));
                    } else equal(CoffeeMachine.Status.INSUFFICIENT, result.status());
                }
                equal(2, prepared);
                equal(remaining, machine.inventory());
                for (long amount : remaining.values()) check(amount >= 0, "no negative stock");
            } finally { stop(machine); }
        }
    }
}
