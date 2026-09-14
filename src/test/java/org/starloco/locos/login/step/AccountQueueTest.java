package org.starloco.locos.login.step;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AccountQueueTest {

    @Test
    void banCountdownIsDaysHoursMinutesRoundedUp() {
        assertThat(AccountQueue.banCountdown(Duration.ofSeconds(30))).isEqualTo("0|0|1");
        assertThat(AccountQueue.banCountdown(Duration.ofMinutes(59))).isEqualTo("0|0|59");
        assertThat(AccountQueue.banCountdown(Duration.ofHours(5).plusMinutes(3)))
                .isEqualTo("0|5|3");
        assertThat(AccountQueue.banCountdown(
                        Duration.ofDays(3).plusHours(23).plusMinutes(59).plusSeconds(1)))
                .as("rounding up the minutes carries over")
                .isEqualTo("4|0|0");
        assertThat(AccountQueue.banCountdown(Duration.ofDays(40))).isEqualTo("40|0|0");
    }
}
