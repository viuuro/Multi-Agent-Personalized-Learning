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

    public boolean tryAcquire(String key, int maxRequests, int windowSeconds) {
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_KEYS) return false;
        long now = Instant.now().getEpochSecond();
        Deque<Long> queue = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
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
    }
}
