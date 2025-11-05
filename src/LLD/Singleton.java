package LLD;

public class Singleton {
	private static volatile Singleton instance; // volatile prevents caching issues
	
	private Singleton() {}
	
	public static Singleton getInstance() {
		if (instance == null) { // first check (no locking)
			synchronized (Singleton.class) {
				if (instance == null) { // second check (with locking)
					instance = new Singleton();
				}
			}
		}
		return instance;
	}
}
