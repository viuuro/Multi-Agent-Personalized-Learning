package com.edu.agent.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestRateLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> trackedWindows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxRequests, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_KEYS) {
            pruneExpired(now);
            if (attempts.size() >= MAX_TRACKED_KEYS) return false;
        }
        Deque<Long> queue = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        trackedWindows.put(key, windowSeconds);
        synchronized (queue) {
            long cutoff = now - windowSeconds;
            while (!queue.isEmpty() && queue.peekFirst() <= cutoff) queue.removeFirst();
            if (queue.size() >= maxRequests) return false;
            queue.addLast(now);
            return true;
        }
    }

    public void clear(String key) {
        attempts.remove(key);
        trackedWindows.remove(key);
    }

    private void pruneExpired(long now) {
        attempts.forEach((trackedKey, queue) -> {
            synchronized (queue) {
                long cutoff = now - trackedWindows.getOrDefault(trackedKey, 60);
                while (!queue.isEmpty() && queue.peekFirst() <= cutoff) queue.removeFirst();
                if (queue.isEmpty() && attempts.remove(trackedKey, queue)) {
                    trackedWindows.remove(trackedKey);
                }
            }
        });
    }
}
