package LLD;

import java.util.*;

public class CommodityPriceStore {
    private final Map<Long, Integer> timestampToPrice = new HashMap<>();
    private final TreeMap<Integer, Integer> priceFrequency = new TreeMap<>();
    private int maxPrice = Integer.MIN_VALUE;

    public void upsert(long timestamp, int price) {
        // If timestamp exists, remove old price
        if (timestampToPrice.containsKey(timestamp)) {
            int oldPrice = timestampToPrice.get(timestamp);
            int count = priceFrequency.get(oldPrice);
            if (count == 1) {
                priceFrequency.remove(oldPrice);
            } else {
                priceFrequency.put(oldPrice, count - 1);
            }
            // If oldPrice was the max and no other entries remain for it
            if (oldPrice == maxPrice && !priceFrequency.containsKey(oldPrice)) {
                maxPrice = priceFrequency.isEmpty() ? Integer.MIN_VALUE : priceFrequency.lastKey();
            }
        }

        // Add new price
        priceFrequency.put(price, priceFrequency.getOrDefault(price, 0) + 1);
        timestampToPrice.put(timestamp, price);

        // Update maxPrice
        if (price > maxPrice) {
            maxPrice = price;
        }
    }

    public int getMaxCommodityPrice() {
        return maxPrice;
    }

    public static void main(String[] args) {
        CommodityPriceStore store = new CommodityPriceStore();

        store.upsert(5, 100);
        store.upsert(2, 200);
        System.out.println(store.getMaxCommodityPrice());
        store.upsert(7, 150);
        store.upsert(2, 50);  // overwrite timestamp=2 with new price

        System.out.println(store.getMaxCommodityPrice());
    }
}
