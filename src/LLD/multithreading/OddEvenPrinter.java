package LLD.multithreading;

/**
 * Two threads print numbers 1..N alternately: one thread prints only odd numbers,
 * the other prints only even numbers, but combined output is sequential (1,2,3,4,...).
 *
 * Approach: shared monitor object + a "turn" flag. Threads wait() when it's not
 * their turn and notifyAll() after printing to wake the other thread.
 *
 * A second variant (OddEvenPrinterLock) below shows the equivalent solution using
 * an explicit Lock + Condition instead of intrinsic synchronized/wait/notify.
 */
public class OddEvenPrinter {

    private final int max;
    private int current = 1;
    private final Object lock = new Object();

    public OddEvenPrinter(int max) {
        this.max = max;
    }

    // Prints odd numbers: 1, 3, 5, ...
    public void printOdd() {
        // Only one thread (odd or even) may hold "lock" at a time.
        synchronized (lock) {
            // Keep looping until every number up to max has been printed by someone.
            while (current <= max) {
                // Not our turn yet (current is even) -> release the lock and sleep
                // on the monitor until someone calls notifyAll(). Must be a while
                // (not if) to guard against spurious wakeups: after waking up we
                // re-check the condition before proceeding.
                while (current % 2 == 0 && current <= max) {
                    try {
                        lock.wait(); // releases 'lock' while waiting, re-acquires before returning
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                // We may have woken up because max was reached by the other thread;
                // in that case there's nothing left for us to print.
                if (current > max) break;

                // It's our turn (current is odd): print, then advance the shared counter.
                System.out.println(Thread.currentThread().getName() + ": " + current);
                current++;

                // Wake up the other (even) thread so it can check whether it's now its turn.
                lock.notifyAll();
            }
        }
    }

    // Prints even numbers: 2, 4, 6, ...
    public void printEven() {
        // Mirror image of printOdd(): wait while it's not our turn (current is odd),
        // print when current is even, then hand control back via notifyAll().
        synchronized (lock) {
            while (current <= max) {
                while (current % 2 != 0 && current <= max) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (current > max) break;
                System.out.println(Thread.currentThread().getName() + ": " + current);
                current++;
                lock.notifyAll();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final int N = 20;
        OddEvenPrinter printer = new OddEvenPrinter(N);

        Thread oddThread = new Thread(printer::printOdd, "Odd-Thread");
        Thread evenThread = new Thread(printer::printEven, "Even-Thread");

        oddThread.start();
        evenThread.start();

        oddThread.join();
        evenThread.join();

        System.out.println("Done printing 1.." + N);

        // Run the Lock/Condition-based variant too
        OddEvenPrinterLock.demo(N);
    }
}

