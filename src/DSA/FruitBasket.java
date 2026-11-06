package DSA;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FruitBasket {

    public static List<List<String>> placeFruits(int[] fruits) {
        List<List<String>> result = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();
        int sum = 0;
        for (int i = 0; i < fruits.length; i++) {
            map.put(String.valueOf(i), fruits[i]);
            sum += fruits[i];
        }

        backtrack(result, map, "", sum, new ArrayList<>());


        return result;

    }


    public static void backtrack(List<List<String>> result, Map<String, Integer> map, String prevFruit, int remaining, List<String> current) {

        if (remaining == 0) {
            result.add(new ArrayList<>(current));
        }

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String key = entry.getKey();
            Integer count = entry.getValue();

            if (key.equals(prevFruit) || count ==0) {
                continue;
            }

            current.add(key);
            map.put(key, count - 1);

            backtrack(result, map, key, remaining - 1, current);
            current.remove(current.size() - 1);
            map.put(key, count);

        }

    }

    public static void main(String arg[]) {

        List<List<String>> result = new ArrayList<>();

        result = placeFruits(new int[]{2, 1, 2});

        for (List<String> r : result) {
            System.out.println();
            for (String s : r) {
                System.out.print(s + " ");
            }
        }


    }


}
