package LLD.coffeemachine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** CLI: config path, optionally followed by beverage names; otherwise order each recipe once. */
public final class CoffeeMachineDemo {
    private CoffeeMachineDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0)
            throw new IllegalArgumentException("Usage: CoffeeMachineDemo <config.json> [beverage ...]");
        MachineConfig config = ConfigJson.parse(new String(
                Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8));
        try (CoffeeMachine machine = new CoffeeMachine(config, 100, 50)) {
            List<Future<CoffeeMachine.Result>> orders = new ArrayList<>();
            if (args.length == 1) {
                for (String name : config.beverages().keySet()) orders.add(machine.order(name));
            } else {
                for (int i = 1; i < args.length; i++) orders.add(machine.order(args[i]));
            }
            for (Future<CoffeeMachine.Result> order : orders) System.out.println(order.get());
            System.out.println("Low ingredients (<= 50): " + machine.lowIngredients());
        }
    }
}
