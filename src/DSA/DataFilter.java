package DSA;

import java.util.ArrayList;
import java.util.List;

public class DataFilter {

    public List<Integer> typeIds;

    public DataFilter(List<Integer> typeIds) {
        this.typeIds = typeIds;
    }

    public static void main(String[] args) {
        List<Integer> tIds = new ArrayList<>();
        tIds.add(1);
        DataFilter dataFilter = new DataFilter(tIds);
        System.out.print(dataFilter.typeIds);

        tIds.add(2);
        System.out.print(dataFilter.typeIds);

        List<Integer> newTIds = dataFilter.typeIds;
        newTIds.add(3);
        System.out.print(dataFilter.typeIds);
    }
}
