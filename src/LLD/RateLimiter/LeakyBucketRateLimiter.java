package LLD.RateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LeakyBucketRateLimiter {
	private final int capacity;          // max requests that can wait in the bucket
	private final double leakRatePerMillis; // leaks per ms
	private final Map<String, UserBucket> userBuckets = new ConcurrentHashMap<>();
	
	public LeakyBucketRateLimiter(int capacity, int leakRate, long leakIntervalMillis) {
		this.capacity = capacity;
		this.leakRatePerMillis = leakRate / (double) leakIntervalMillis;
	}
	
	public boolean allowRequest(String userId) {
		UserBucket bucket = userBuckets.computeIfAbsent(userId,
				id -> new UserBucket(capacity, leakRatePerMillis));
		return bucket.allowRequest();
	}
	
	// -------- Per-user bucket --------
	private static class UserBucket {
		private final int capacity;
		private final double leakRatePerMillis;
		private double water;            // current "fill" level
		private long lastLeakTimestamp;
		
		public UserBucket(int capacity, double leakRatePerMillis) {
			this.capacity = capacity;
			this.leakRatePerMillis = leakRatePerMillis;
			this.water = 0.0;
			this.lastLeakTimestamp = System.currentTimeMillis();
		}
		
		public synchronized boolean allowRequest() {
			leak();
			
			if (water < capacity) {
				water += 1; // add request (like pouring water in bucket)
				return true;
			}
			return false; // bucket full → reject
		}
		
		private void leak() {
			long now = System.currentTimeMillis();
			long elapsed = now - lastLeakTimestamp;
			
			if (elapsed > 0) {
				double leaked = elapsed * leakRatePerMillis;
				water = Math.max(0, water - leaked); // leak water
				lastLeakTimestamp = now;
			}
		}
	}
	
	// ---------- Example ----------
	public static void main(String[] args) throws InterruptedException {
		// Bucket size = 5, leak = 1 request per 1000ms
		LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(5, 1, 1000);
		
		String user = "alice";
		
		// Burst of 7 requests quickly
		for (int i = 1; i <= 7; i++) {
			System.out.println("Request " + i + " allowed? " + limiter.allowRequest(user));
			Thread.sleep(200); // 200ms apart
		}
		
		// Wait 3 seconds for leaks
		Thread.sleep(3000);
		System.out.println("After 3s wait: allowed? " + limiter.allowRequest(user));
	}
}
