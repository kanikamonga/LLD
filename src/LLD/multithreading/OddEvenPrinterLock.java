package LLD.multithreading;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock/Condition-based equivalent of OddEvenPrinter.
 * <p>
 * Uses an explicit ReentrantLock with a single Condition ("turnChanged").
 * Semantics are identical to the wait/notify version, but:
 * - Lock/unlock must be done explicitly (finally block).
 * - Condition.await()/signalAll() replace Object.wait()/notifyAll().
 * - Explicit Lock allows features not available with intrinsic locks,
 * e.g., tryLock(), fairness policies, or multiple Conditions per lock.
 */
class OddEvenPrinterLock {

    private final int max;
    private int current = 1;
    private final Lock lock = new ReentrantLock();
    private final Condition turnChanged = lock.newCondition();

    public OddEvenPrinterLock(int max) {
        this.max = max;
    }

    public void printOdd() {
        // Explicit lock: must acquire before touching shared state, and must
        // release in a finally block so we don't deadlock if an exception occurs.
        lock.lock();
        try {
            while (current <= max) {
                // Same "wait while not our turn" logic as before, but using
                // Condition.await() which is the Lock-API equivalent of Object.wait().
                // It atomically releases 'lock' and suspends the thread; on wakeup it
                // re-acquires 'lock' before returning, so we still must re-check the
                // condition in a while-loop (spurious wakeup protection).
                while (current % 2 == 0 && current <= max) {
                    try {
                        turnChanged.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (current > max) break;
                System.out.println(Thread.currentThread().getName() + ": " + current);
                current++;
                // signalAll() is the Condition equivalent of notifyAll(): wakes every
                // thread waiting on 'turnChanged' so the even-thread can re-check its turn.
                turnChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void printEven() {
        // Mirror of printOdd(): waits while current is odd, prints when even.
        lock.lock();
        try {
            while (current <= max) {
                while (current % 2 != 0 && current <= max) {
                    try {
                        turnChanged.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (current > max) break;
                System.out.println(Thread.currentThread().getName() + ": " + current);
                current++;
                turnChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    static void demo(int n) throws InterruptedException {
        OddEvenPrinterLock printer = new OddEvenPrinterLock(n);

        Thread oddThread = new Thread(printer::printOdd, "Odd-Lock-Thread");
        Thread evenThread = new Thread(printer::printEven, "Even-Lock-Thread");

        System.out.println("--- Lock/Condition based version ---");
        oddThread.start();
        evenThread.start();

        oddThread.join();
        evenThread.join();

        System.out.println("Done printing 1.." + n + " (Lock/Condition version)");
    }
}
