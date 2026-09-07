package LLD.LockerManagement;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LockerManagementService {
    private final Map<String, Locker> lockersById = new HashMap<>();
    private final Map<LockerSize, LinkedHashSet<String>> availableLockerIds =
            new EnumMap<>(LockerSize.class);

    public LockerManagementService() {
        for (LockerSize size : LockerSize.values()) {
            availableLockerIds.put(size, new LinkedHashSet<>());
        }
    }

    public synchronized void addLocker(String size) {
        addLockerAndGet(size);
    }

    public synchronized Locker addLockerAndGet(String size) {
        LockerSize lockerSize = LockerSize.from(size);
        String lockerId = UUID.randomUUID().toString();
        Locker locker = new Locker(lockerId, lockerSize);
        lockersById.put(lockerId, locker);
        availableLockerIds.get(lockerSize).add(lockerId);
        return locker;
    }

    public synchronized Locker findLocker(String size) {
        LockerSize lockerSize = LockerSize.from(size);
        Set<String> availableIds = availableLockerIds.get(lockerSize);

        for (String lockerId : availableIds) {
            return lockersById.get(lockerId);
        }
        return null;
    }

    public synchronized boolean storePackage(String lockerId) {
        Locker locker = lockersById.get(lockerId);
        if (locker == null || !locker.occupy()) {
            return false;
        }

        availableLockerIds.get(locker.getSize()).remove(lockerId);
        return true;
    }

    public synchronized boolean removePackage(String lockerId) {
        Locker locker = lockersById.get(lockerId);
        if (locker == null || !locker.release()) {
            return false;
        }

        availableLockerIds.get(locker.getSize()).add(lockerId);
        return true;
    }

    public synchronized int getAvailableCount(String size) {
        return availableLockerIds.get(LockerSize.from(size)).size();
    }

    public synchronized int getTotalCount(String size) {
        LockerSize lockerSize = LockerSize.from(size);
        int count = 0;
        for (Locker locker : lockersById.values()) {
            if (locker.getSize() == lockerSize) {
                count++;
            }
        }
        return count;
    }
}
