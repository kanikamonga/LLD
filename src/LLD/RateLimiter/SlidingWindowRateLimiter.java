package LLD.RateLimiter;

import java.util.*;
import java.util.concurrent.*;

public class SlidingWindowRateLimiter {
	private final int maxRequests;               // X requests
	private final long windowSizeInMillis;       // Y seconds
	private final Map<String, UserBucket> userBuckets = new ConcurrentHashMap<>();
	
	public SlidingWindowRateLimiter(int maxRequests, long windowSizeInMillis) {
		this.maxRequests = maxRequests;
		this.windowSizeInMillis = windowSizeInMillis;
	}
	
	public boolean allowRequest(String userId) {
		UserBucket bucket = userBuckets.computeIfAbsent(userId,
				id -> new UserBucket(maxRequests, windowSizeInMillis));
		return bucket.allowRequest();
	}
	
	// -------- Per-user state --------
	private static class UserBucket {
		private final int maxRequests;
		private final long windowSizeInMillis;
		private final Deque<Long> requestTimestamps;
		
		public UserBucket(int maxRequests, long windowSizeInMillis) {
			this.maxRequests = maxRequests;
			this.windowSizeInMillis = windowSizeInMillis;
			this.requestTimestamps = new ArrayDeque<>();
		}
		
		public synchronized boolean allowRequest() {
			long now = System.currentTimeMillis();
			
			// Remove old requests outside the window
			while (!requestTimestamps.isEmpty() &&
					now - requestTimestamps.peekFirst() >= windowSizeInMillis) {
				requestTimestamps.pollFirst();
			}
			
			if (requestTimestamps.size() < maxRequests) {
				requestTimestamps.addLast(now);
				return true;
			}
			return false;
		}
	}
	
	// ---------- Example ----------
	public static void main(String[] args) throws InterruptedException {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 5000); // 3 req / 5 sec
		
		String user = "alice";
		
		for (int i = 1; i <= 5; i++) {
			System.out.println("Request " + i + " allowed? " + limiter.allowRequest(user));
			Thread.sleep(1000);
		}
		
		Thread.sleep(3000);
		System.out.println("After 3s wait: allowed? " + limiter.allowRequest(user));
	}
}
