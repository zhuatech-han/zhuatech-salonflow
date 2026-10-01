// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.time.*;
import org.junit.jupiter.api.Test;

/** 时区边界和精度规则独立回归。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class BookingPolicyTest {
  @Test
  void boundaryIntervalsDoNotOverlap() {
    var a = Instant.parse("2026-10-01T01:00:00Z");
    assertFalse(BookingPolicy.overlap(a, a.plusSeconds(60), a.plusSeconds(60), a.plusSeconds(120)));
    assertTrue(BookingPolicy.overlap(a, a.plusSeconds(120), a.plusSeconds(60), a.plusSeconds(180)));
  }

  @Test
  void daylightSavingMissingAndDuplicateTimesAreRejected() {
    assertThrows(
        Problem.class,
        () -> BookingPolicy.local(LocalDate.of(2026, 3, 8), 150, ZoneId.of("America/New_York")));
    assertThrows(
        Problem.class,
        () -> BookingPolicy.local(LocalDate.of(2026, 11, 1), 90, ZoneId.of("America/New_York")));
  }

  @Test
  void localMidnightMapsToCorrectUtcDate() {
    assertEquals(
        Instant.parse("2026-09-30T16:00:00Z"),
        BookingPolicy.local(LocalDate.of(2026, 10, 1), 0, ZoneId.of("Asia/Shanghai")));
  }

  @Test
  void paymentPrecisionIsNotRoundedSilently() {
    assertThrows(Problem.class, () -> BookingPolicy.money("1.001", true));
    assertThrows(Problem.class, () -> BookingPolicy.money("-1", false));
    assertThrows(Problem.class, () -> BookingPolicy.money("0", true));
    assertEquals(new BigDecimal("0.00"), BookingPolicy.money("0", false));
  }

  @Test
  void csvPreventsFormulaPayloadButPreservesBusinessText() {
    assertEquals("\"'=1+1\"", BookingPolicy.csv("=1+1"));
    assertEquals("\"a\"\"b\"", BookingPolicy.csv("a\"b"));
  }

  @Test
  void secondsAndFractionalEntityIdsAreRejected() {
    assertThrows(Problem.class, () -> BookingPolicy.instant("2026-10-01T01:00:01Z"));
    assertThrows(Problem.class, () -> BookingPolicy.integer("1.5", 0, 10));
  }

  @Test
  void explicitUtcCannotBypassDuplicateLocalTime() {
    var start = Instant.parse("2026-11-01T05:30:00Z");
    assertThrows(
        Problem.class,
        () ->
            BookingPolicy.unambiguousInterval(
                start, start.plusSeconds(900), ZoneId.of("America/New_York")));
    assertDoesNotThrow(
        () ->
            BookingPolicy.unambiguousInterval(
                Instant.parse("2026-11-01T08:00:00Z"),
                Instant.parse("2026-11-01T08:45:00Z"),
                ZoneId.of("America/New_York")));
  }

  @Test
  void serviceAcrossClockJumpIsRejected() {
    assertThrows(
        Problem.class,
        () ->
            BookingPolicy.unambiguousInterval(
                Instant.parse("2026-03-08T06:45:00Z"),
                Instant.parse("2026-03-08T07:30:00Z"),
                ZoneId.of("America/New_York")));
    assertThrows(
        Problem.class,
        () ->
            BookingPolicy.unambiguousInterval(
                Instant.parse("2026-11-01T04:45:00Z"),
                Instant.parse("2026-11-01T07:30:00Z"),
                ZoneId.of("America/New_York")));
  }
}
