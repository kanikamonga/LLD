package LLD.RateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketRateLimiter {
	private final int capacity;               // max tokens in bucket
	private final double refillRatePerMillis; // tokens added per ms
	private final Map<String, UserBucket> userBuckets = new ConcurrentHashMap<>();
	
	public TokenBucketRateLimiter(int capacity, int refillTokens, long refillIntervalMillis) {
		this.capacity = capacity;
		this.refillRatePerMillis = refillTokens / (double) refillIntervalMillis;
	}
	
	public boolean allowRequest(String userId) {
		UserBucket bucket = userBuckets.computeIfAbsent(userId,
				id -> new UserBucket(capacity, refillRatePerMillis));
		return bucket.allowRequest();
	}
	
	// -------- Per-user bucket --------
	private static class UserBucket {
		private final int capacity;
		private final double refillRatePerMillis;
		private double tokens;
		private long lastRefillTimestamp;
		
		public UserBucket(int capacity, double refillRatePerMillis) {
			this.capacity = capacity;
			this.refillRatePerMillis = refillRatePerMillis;
			this.tokens = capacity; // start full
			this.lastRefillTimestamp = System.currentTimeMillis();
		}
		
		public synchronized boolean allowRequest() {
			refill();
			
			if (tokens >= 1) {
				tokens -= 1;
				return true;
			}
			return false;
		}
		
		private void refill() {
			long now = System.currentTimeMillis();
			long elapsed = now - lastRefillTimestamp;
			
			if (elapsed > 0) {
				double tokensToAdd = elapsed * refillRatePerMillis;
				tokens = Math.min(capacity, tokens + tokensToAdd);
				lastRefillTimestamp = now;
			}
		}
	}
	
	// ---------- Example ----------
	public static void main(String[] args) throws InterruptedException {
		// Bucket of 5 tokens, refills 1 token every 1000ms
		TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1, 1000);
		
		String user = "alice";
		
		for (int i = 1; i <= 7; i++) {
			System.out.println("Request " + i + " allowed? " + limiter.allowRequest(user));
			Thread.sleep(200); // request every 200ms
		}
		
		Thread.sleep(3000); // wait for refill
		System.out.println("After 3s wait: allowed? " + limiter.allowRequest(user));
	}
}
