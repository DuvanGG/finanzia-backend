package com.finanzia.infrastructure.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SystemClockTest {

    @Test
    void returnsCurrentTime() {
        Instant before = Instant.now();
        Instant value = new SystemClock().now();
        Instant after = Instant.now();

        assertThat(value).isBetween(before, after.plus(Duration.ofSeconds(1)));
        assertThat(Duration.between(before, value)).isLessThan(Duration.ofSeconds(5));
    }
}