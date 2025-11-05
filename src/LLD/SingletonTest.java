package LLD;

public class SingletonTest {
    public static void main(String[] args) {
        // Create 5 threads trying to get Singleton instance
        Runnable task = () -> {
            Singleton singleton = Singleton.getInstance();
            System.out.println("Got instance hash: " + singleton.hashCode() +
                               " | Thread: " + Thread.currentThread().getName());
        };

        Thread t1 = new Thread(task, "Thread-1");
        Thread t2 = new Thread(task, "Thread-2");
        Thread t3 = new Thread(task, "Thread-3");
        Thread t4 = new Thread(task, "Thread-4");
        Thread t5 = new Thread(task, "Thread-5");

        t1.start();
        t2.start();
        t3.start();
        t4.start();
        t5.start();
    }
}
