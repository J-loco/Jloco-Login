package org.starloco.locos.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ConnectionRateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-09-14T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final ConnectionRateLimiter limiter = new ConnectionRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void limitsConnectionsPerIpWithinTheWindow() {
        assertThat(limiter.tryAcquire("198.51.100.1")).isTrue();
        assertThat(limiter.tryAcquire("198.51.100.1")).isTrue();
        assertThat(limiter.tryAcquire("198.51.100.1")).isTrue();
        assertThat(limiter.tryAcquire("198.51.100.1")).isFalse();
        assertThat(limiter.tryAcquire("198.51.100.2"))
                .as("other IPs are not affected")
                .isTrue();
    }

    @Test
    void neverBansPermanently() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("198.51.100.1");
        }
        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire("198.51.100.1"))
                .as("refused attempts do not extend the wait")
                .isFalse();
        clock.advance(Duration.ofSeconds(31));
        assertThat(limiter.tryAcquire("198.51.100.1")).isTrue();
    }

    @Test
    void purgeForgetsIdleIps() {
        limiter.tryAcquire("198.51.100.1");
        limiter.tryAcquire("198.51.100.2");
        clock.advance(Duration.ofSeconds(90));
        limiter.tryAcquire("198.51.100.3");
        limiter.purge();
        assertThat(limiter.trackedIps()).isEqualTo(1);
    }
}
