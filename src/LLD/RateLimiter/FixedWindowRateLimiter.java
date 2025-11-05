package LLD.RateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FixedWindowRateLimiter {
	private final int maxRequests;            // X = max requests
	private final long windowSizeInMillis;    // Y = window duration
	private final Map<String, UserBucket> userBuckets = new ConcurrentHashMap<>();
	
	public FixedWindowRateLimiter(int maxRequests, long windowSizeInMillis) {
		this.maxRequests = maxRequests;
		this.windowSizeInMillis = windowSizeInMillis;
	}
	
	public boolean allowRequest(String userId) {
		UserBucket bucket = userBuckets.computeIfAbsent(userId, id ->
				new UserBucket(maxRequests, windowSizeInMillis));
		return bucket.allowRequest();
	}
	
	// -------- Inner class (per-user state) --------
	private static class UserBucket {
		private final int maxRequests;
		private final long windowSizeInMillis;
		private int requestCount;
		private long windowStart;
		
		public UserBucket(int maxRequests, long windowSizeInMillis) {
			this.maxRequests = maxRequests;
			this.windowSizeInMillis = windowSizeInMillis;
			this.requestCount = 0;
			this.windowStart = System.currentTimeMillis();
		}
		
		public synchronized boolean allowRequest() {
			long now = System.currentTimeMillis();
			
			// Check if window expired → reset
			if (now - windowStart >= windowSizeInMillis) {
				windowStart = now;
				requestCount = 0;
			}
			
			if (requestCount < maxRequests) {
				requestCount++;
				return true;
			}
			return false;
		}
	}
	
	// ---------- Example Usage ----------
	public static void main(String[] args) throws InterruptedException {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 5000); // 3 req / 5 sec
		
		String userA = "alice";
		for (int i = 1; i <= 5; i++) {
			System.out.println("Request " + i + " from " + userA + " allowed? "
					+ limiter.allowRequest(userA));
			Thread.sleep(1000);
		}
		
	}
}
