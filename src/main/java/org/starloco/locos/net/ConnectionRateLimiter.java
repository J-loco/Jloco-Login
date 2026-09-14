package org.starloco.locos.net;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits new connections per client IP over a sliding window. Refused attempts do not count, and nothing
 * is banned permanently: an IP is allowed again as soon as its oldest connections leave the window.
 */
public final class ConnectionRateLimiter {

    private final int maxConnections;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public ConnectionRateLimiter(int maxConnections, Duration window, Clock clock) {
        this.maxConnections = maxConnections;
        this.window = window;
        this.clock = clock;
    }

    /** Records a connection from the IP and tells whether it is allowed. */
    public boolean tryAcquire(String ip) {
        long now = clock.millis();
        Deque<Long> times = attempts.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (times) {
            evict(times, now);
            if (times.size() >= maxConnections) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** Forgets the IPs without recent connections (called periodically). */
    public void purge() {
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> {
            Deque<Long> times = entry.getValue();
            synchronized (times) {
                evict(times, now);
                return times.isEmpty();
            }
        });
    }

    int trackedIps() {
        return attempts.size();
    }

    private void evict(Deque<Long> times, long now) {
        long oldest = now - window.toMillis();
        while (!times.isEmpty() && times.peekFirst() <= oldest) {
            times.removeFirst();
        }
    }
}
