// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP验收真实预约冲突、服务结账、原单退款、隔离权限及通知持久化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
class SalonIntegrationTest {
  static final String PASSWORD = "Test" + UUID.randomUUID() + "Aa9";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:salonflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("salonflow.admin-password", () -> PASSWORD);
    r.add("salonflow.seed-demo", () -> false);
    r.add("salonflow.smtp-host", () -> "");
    r.add("salonflow.smtp-port", () -> 587);
    r.add("salonflow.smtp-user", () -> "");
    r.add("salonflow.smtp-password", () -> "");
    r.add("salonflow.smtp-from", () -> "");
    r.add("salonflow.smtp-starttls", () -> true);
    r.add("salonflow.smtp-ssl", () -> false);
    r.add("salonflow.reminders-enabled", () -> false);
  }

  /** 可移动UTC时钟，确定性验证接待、提前取消、报表及通知时点。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-01T01:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  @Autowired Store db;
  @Autowired TransactionTemplate tx;
  @Autowired BookingService bookings;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, stylist, otherStylist;
  long category, service, resource, staff, otherStaff, customer, accountId, otherAccountId;
  String suffix;
  Instant start;

  @BeforeEach
  void setup() throws Exception {
    clock.now = Instant.parse("2026-10-01T01:00:00Z");
    start = clock.now.plusSeconds(1800);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin", PASSWORD);
    category =
        postAs(
                admin,
                "/master/categories",
                Map.of("name", "Test " + suffix, "departmentId", 1, "enabled", true),
                200)
            .get("id")
            .asLong();
    service =
        postAs(admin, "/master/services", serviceBody("100.00", 30, 15), 200).get("id").asLong();
    resource =
        postAs(
                admin,
                "/master/resources",
                Map.of(
                    "code", "R" + suffix, "name", "Test room", "departmentId", 1, "enabled", true),
                200)
            .get("id")
            .asLong();
    accountId = user("s" + suffix, "服务员工 / Stylist", 1);
    otherAccountId = user("z" + suffix, "服务员工 / Stylist", 1);
    stylist = login("s" + suffix, PASSWORD);
    otherStylist = login("z" + suffix, PASSWORD);
    staff = postAs(admin, "/master/staff", staffBody(accountId), 200).get("id").asLong();
    otherStaff = postAs(admin, "/master/staff", staffBody(otherAccountId), 200).get("id").asLong();
    for (var person : List.of(staff, otherStaff))
      for (int i = 1; i <= 7; i++)
        postAs(admin, "/master/shifts", shift(person, i, 0, 1440, 0, 0), 200);
    customer =
        postAs(
                admin,
                "/master/customers",
                Map.of(
                    "name",
                    "Test client " + suffix,
                    "departmentId",
                    1,
                    "enabled",
                    true,
                    "email",
                    "test-" + suffix + "@example.invalid",
                    "emailConsent",
                    true),
                200)
            .get("id")
            .asLong();
  }

  Map<String, Object> serviceBody(String price, int duration, int buffer) {
    return Map.of(
        "code",
        "S" + suffix,
        "name",
        "Test service",
        "categoryId",
        category,
        "departmentId",
        1,
        "enabled",
        true,
        "price",
        price,
        "durationMinutes",
        duration,
        "bufferMinutes",
        buffer,
        "resourceRequired",
        true);
  }

  Map<String, Object> staffBody(long a) {
    return Map.of(
        "name",
        "Test stylist",
        "accountId",
        a,
        "departmentId",
        1,
        "services",
        List.of(service),
        "enabled",
        true);
  }

  Map<String, Object> shift(long id, int day, int open, int close, int bs, int be) {
    return Map.of(
        "departmentId",
        1,
        "staffId",
        id,
        "weekday",
        day,
        "startMinute",
        open,
        "endMinute",
        close,
        "breakStart",
        bs,
        "breakEnd",
        be);
  }

  Map<String, Object> bookBody(Instant time, long staffId, long room) {
    return new HashMap<>(
        Map.of(
            "serviceId",
            service,
            "staffId",
            staffId,
            "resourceId",
            room,
            "customerId",
            customer,
            "startsAt",
            time.toString(),
            "requestKey",
            UUID.randomUUID().toString()));
  }

  long book() throws Exception {
    return postAs(admin, "/appointments", bookBody(start, staff, resource), 200).get("id").asLong();
  }

  JsonNode getAs(MockHttpSession s, String path, int status) throws Exception {
    var r = mvc.perform(get("/api" + path).session(s)).andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode send(MockHttpSession s, MockHttpServletRequestBuilder b, Object body, int status)
      throws Exception {
    b.contentType("application/json").content(json.writeValueAsString(body)).with(csrf());
    if (s != null) b.session(s);
    else
      b.with(
          r -> {
            r.setRemoteAddr("test-" + suffix);
            return r;
          });
    var r = mvc.perform(b).andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode postAs(MockHttpSession s, String path, Object body, int status) throws Exception {
    return send(s, post("/api" + path), body, status);
  }

  MockHttpSession login(String name, String password) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  long user(String name, String role, long dept) throws Exception {
    long rid = 0;
    for (var r : getAs(admin, "/lists/roles?size=100", 200).get("items"))
      if (r.get("name").asString().equals(role)) rid = r.get("id").asLong();
    return postAs(
            admin,
            "/admin/users",
            Map.of(
                "username",
                name,
                "displayName",
                name,
                "password",
                PASSWORD,
                "departmentId",
                dept,
                "roleId",
                rid,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  JsonNode appointment(long id) throws Exception {
    return getAs(admin, "/appointments/" + id, 200).get("appointment");
  }

  Map<String, Object> command(long id, Object... pairs) throws Exception {
    var m = new HashMap<String, Object>();
    m.put("revision", appointment(id).get("revision").asLong());
    m.put("requestKey", UUID.randomUUID().toString());
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  void action(MockHttpSession s, long id, String a, Object... pairs) throws Exception {
    postAs(s, "/appointments/" + id + "/" + a, command(id, pairs), 200);
  }

  void served(long id) throws Exception {
    clock.now = start;
    action(admin, id, "arrive");
    action(stylist, id, "start");
    clock.now = start.plusSeconds(1800);
    action(stylist, id, "finish", "note", "Service completed");
  }

  void complete(long id) throws Exception {
    served(id);
    action(admin, id, "checkout", "discount", "10.00", "note", "Approved discount");
    action(admin, id, "pay", "amount", "90.00", "method", "CASH", "reference", "TEST-CASH");
  }

  @Test
  void completeServiceDiscountPaymentAndPartialRefundAreReconciled() throws Exception {
    long id = book();
    complete(id);
    assertEquals("COMPLETED", appointment(id).get("status").asString());
    long pay = getAs(admin, "/appointments/" + id, 200).get("payments").get(0).get("id").asLong();
    action(
        admin,
        id,
        "refund",
        "sourceId",
        pay,
        "amount",
        "20.00",
        "reference",
        "TEST-RETURN",
        "note",
        "Refund approved");
    var r = getAs(admin, "/reports?from=2026-10-01&to=2026-10-01", 200);
    assertTrue(r.get("receipts").decimalValue().compareTo(new BigDecimal("90")) >= 0);
    assertEquals(
        0, new BigDecimal("20.00").compareTo(appointment(id).get("refunded").decimalValue()));
    assertEquals(0, new BigDecimal("90.00").compareTo(appointment(id).get("total").decimalValue()));
  }

  @Test
  void simultaneousAppointmentsCannotBookSameStaff() throws Exception {
    var bodies = List.of(bookBody(start, staff, resource), bookBody(start, staff, resource));
    var pool = Executors.newFixedThreadPool(2);
    try {
      var jobs = new ArrayList<Future<Integer>>();
      for (var body : bodies)
        jobs.add(
            pool.submit(
                () ->
                    mvc.perform(
                            post("/api/appointments")
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content(json.writeValueAsString(body)))
                        .andReturn()
                        .getResponse()
                        .getStatus()));
      var codes = new ArrayList<Integer>();
      for (var f : jobs) codes.add(f.get());
      Collections.sort(codes);
      assertEquals(List.of(200, 409), codes);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void resourceCannotBeBookedByDifferentStaff() throws Exception {
    book();
    assertEquals(
        "SLOT_TAKEN",
        postAs(admin, "/appointments", bookBody(start, otherStaff, resource), 409)
            .get("code")
            .asString());
  }

  @Test
  void bufferIsReservedAndExactEndBoundaryIsAvailable() throws Exception {
    book();
    postAs(admin, "/appointments", bookBody(start.plusSeconds(1800), staff, resource), 409);
    postAs(admin, "/appointments", bookBody(start.plusSeconds(2700), staff, resource), 200);
  }

  @Test
  void confirmedCancellationReleasesCapacityAndPreservesHistory() throws Exception {
    long id = book();
    action(admin, id, "cancel", "note", "Customer request");
    book();
    assertEquals("CANCELLED", appointment(id).get("status").asString());
    assertEquals(2, getAs(admin, "/appointments/" + id, 200).get("events").size());
  }

  @Test
  void repeatBookingKeyReturnsOriginalAndChangedInputIsRejected() throws Exception {
    var v = bookBody(start, staff, resource);
    var a = postAs(admin, "/appointments", v, 200);
    var b = postAs(admin, "/appointments", v, 200);
    assertEquals(a.get("id").asLong(), b.get("id").asLong());
    v.put("startsAt", start.plusSeconds(3600).toString());
    postAs(admin, "/appointments", v, 409);
  }

  @Test
  void oldRevisionCannotOverrideRecentCheckin() throws Exception {
    long id = book();
    var old = command(id, "note", "Stale cancel");
    clock.now = start;
    action(admin, id, "arrive");
    assertEquals(
        "STALE_REVISION",
        postAs(admin, "/appointments/" + id + "/cancel", old, 409).get("code").asString());
  }

  @Test
  void administratorCannotServeForTheStylist() throws Exception {
    long id = book();
    clock.now = start;
    action(admin, id, "arrive");
    assertEquals(
        "NOT_ASSIGNED_STYLIST",
        postAs(admin, "/appointments/" + id + "/start", command(id), 403).get("code").asString());
  }

  @Test
  void otherStylistCannotReadOrExecuteAppointment() throws Exception {
    long id = book();
    getAs(otherStylist, "/appointments/" + id, 403);
    var rows = getAs(otherStylist, "/lists/appointments", 200).get("items");
    assertEquals(0, rows.size());
    postAs(otherStylist, "/appointments/" + id + "/start", command(id), 403);
  }

  @Test
  void checkoutBeforeFinishingIsRejected() throws Exception {
    long id = book();
    postAs(admin, "/appointments/" + id + "/checkout", command(id, "discount", "0"), 409);
  }

  @Test
  void partialPaymentsAndRetriesNeverDoublePost() throws Exception {
    long id = book();
    served(id);
    action(admin, id, "checkout", "discount", "0");
    var v = command(id, "amount", "40.00", "method", "CASH", "reference", "PART1");
    postAs(admin, "/appointments/" + id + "/pay", v, 200);
    postAs(admin, "/appointments/" + id + "/pay", v, 200);
    assertEquals(0, new BigDecimal("40.00").compareTo(appointment(id).get("paid").decimalValue()));
    assertEquals("SERVED", appointment(id).get("status").asString());
    action(admin, id, "pay", "amount", "60.00", "method", "BANK", "reference", "PART2");
    assertEquals("COMPLETED", appointment(id).get("status").asString());
  }

  @Test
  void overpaymentRollsBackLedgerAndAmount() throws Exception {
    long id = book();
    served(id);
    action(admin, id, "checkout", "discount", "0");
    postAs(
        admin,
        "/appointments/" + id + "/pay",
        command(id, "amount", "100.01", "method", "CASH", "reference", "OVER"),
        409);
    assertEquals(0, getAs(admin, "/appointments/" + id, 200).get("payments").size());
    assertEquals(0, new BigDecimal("0.00").compareTo(appointment(id).get("paid").decimalValue()));
  }

  @Test
  void originalPaymentBoundsCumulativeRefund() throws Exception {
    long id = book();
    complete(id);
    long pay = getAs(admin, "/appointments/" + id, 200).get("payments").get(0).get("id").asLong();
    action(
        admin, id, "refund", "sourceId", pay, "amount", "60", "reference", "R1", "note", "Reason");
    postAs(
        admin,
        "/appointments/" + id + "/refund",
        command(id, "sourceId", pay, "amount", "31", "reference", "R2", "note", "Reason"),
        409);
    assertEquals(
        0, new BigDecimal("60.00").compareTo(appointment(id).get("refunded").decimalValue()));
  }

  @Test
  void freeServiceStillCompletesWithoutFakeZeroPayment() throws Exception {
    send(admin, put("/api/master/services/" + service), serviceBody("0", 30, 15), 200);
    long id = book();
    served(id);
    action(admin, id, "checkout", "discount", "0");
    assertEquals("COMPLETED", appointment(id).get("status").asString());
    assertEquals(0, getAs(admin, "/appointments/" + id, 200).get("payments").size());
  }

  @Test
  void servicePriceAndDurationStayFrozenWhenCatalogChanges() throws Exception {
    long id = book();
    send(admin, put("/api/master/services/" + service), serviceBody("180", 60, 0), 200);
    assertEquals(
        0, new BigDecimal("100.00").compareTo(appointment(id).get("price").decimalValue()));
    action(
        admin,
        id,
        "reschedule",
        "startsAt",
        start.plusSeconds(3600).toString(),
        "staffId",
        staff,
        "resourceId",
        resource);
    assertEquals(30, appointment(id).get("durationMinutes").asInt());
  }

  @Test
  void breakBlocksBookingAndShiftChangeCannotInvalidateExistingBooking() throws Exception {
    var shifts = getAs(admin, "/lists/shifts?size=100", 200).get("items");
    long shiftId = 0;
    for (var w : shifts)
      if (w.get("staffId").asLong() == staff && w.get("weekday").asInt() == 4)
        shiftId = w.get("id").asLong();
    send(admin, put("/api/master/shifts/" + shiftId), shift(staff, 4, 0, 1440, 570, 600), 200);
    postAs(admin, "/appointments", bookBody(start, staff, resource), 409);
    send(admin, put("/api/master/shifts/" + shiftId), shift(staff, 4, 0, 1440, 0, 0), 200);
    book();
    send(admin, put("/api/master/shifts/" + shiftId), shift(staff, 4, 600, 1440, 0, 0), 409);
  }

  @Test
  void staffLeaveCannotHideConfirmedAppointment() throws Exception {
    book();
    postAs(
        admin,
        "/master/blocks",
        Map.of(
            "departmentId",
            1,
            "staffId",
            staff,
            "startsAt",
            start.toString(),
            "endsAt",
            start.plusSeconds(3600).toString(),
            "reason",
            "Leave"),
        409);
  }

  @Test
  void resourceBlockPreventsNewBookings() throws Exception {
    postAs(
        admin,
        "/master/blocks",
        Map.of(
            "departmentId",
            1,
            "resourceId",
            resource,
            "startsAt",
            start.toString(),
            "endsAt",
            start.plusSeconds(3600).toString(),
            "reason",
            "Closed"),
        200);
    assertEquals(
        "BLOCKED_TIME",
        postAs(admin, "/appointments", bookBody(start, staff, resource), 409)
            .get("code")
            .asString());
  }

  @Test
  void customerRegistrationCannotEscalateRoleAndCanOnlySeeOwnBookings() throws Exception {
    var c = client("c" + suffix);
    long id = postAs(c, "/appointments", bookBody(start, staff, resource), 200).get("id").asLong();
    assertNotEquals(customer, appointment(id).get("customerId").asLong());
    getAs(c, "/lists/users", 403);
    getAs(c, "/reports?from=2026-10-01&to=2026-10-01", 403);
    getAs(c, "/lists/customers", 403);
    var other = client("d" + suffix);
    getAs(other, "/appointments/" + id, 403);
    assertEquals(0, getAs(other, "/lists/appointments", 200).get("items").size());
  }

  @Test
  void customerNoticeWindowPreventsLateCancellation() throws Exception {
    var c = client("c" + suffix);
    long id =
        postAs(c, "/appointments", bookBody(start.plusSeconds(7200), staff, resource), 200)
            .get("id")
            .asLong();
    clock.now = start.plusSeconds(7100);
    assertEquals(
        "CANCELLATION_NOTICE",
        postAs(c, "/appointments/" + id + "/cancel", command(id, "note", "Late"), 409)
            .get("code")
            .asString());
  }

  @Test
  void customerCannotOverlapTheirBookingsAcrossDifferentStylists() throws Exception {
    var c = client("c" + suffix);
    postAs(c, "/appointments", bookBody(start, staff, resource), 200);
    var r =
        postAs(
                admin,
                "/master/resources",
                Map.of(
                    "code",
                    "ALT" + suffix,
                    "name",
                    "Other room",
                    "departmentId",
                    1,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    postAs(c, "/appointments", bookBody(start, otherStaff, r), 409);
  }

  @Test
  void ongoingServiceBlocksNewBookingEvenAfterPlannedEnd() throws Exception {
    long id = book();
    clock.now = start;
    action(admin, id, "arrive");
    action(stylist, id, "start");
    clock.now = start.plusSeconds(3600);
    postAs(admin, "/appointments", bookBody(clock.now.plusSeconds(1800), staff, resource), 409);
    action(stylist, id, "finish", "note", "Completed overrun");
    postAs(admin, "/appointments", bookBody(clock.now.plusSeconds(1800), staff, resource), 200);
  }

  @Test
  void earlyCompletionDoesNotReleaseCleanupBuffer() throws Exception {
    long id = book();
    clock.now = start;
    action(admin, id, "arrive");
    action(stylist, id, "start");
    action(stylist, id, "finish", "note", "Completed early");
    action(admin, id, "checkout", "discount", "100", "note", "Free courtesy");
    postAs(admin, "/appointments", bookBody(start.plusSeconds(1800), staff, resource), 409);
  }

  @Test
  void noShowCannotBeRecordedBeforeScheduledEnd() throws Exception {
    long id = book();
    postAs(admin, "/appointments/" + id + "/no-show", command(id, "note", "No show"), 409);
    clock.now = start.plusSeconds(1800);
    action(admin, id, "no-show", "note", "Did not arrive");
    assertEquals("NO_SHOW", appointment(id).get("status").asString());
  }

  @Test
  void anonymousAndMissingCsrfWritesAreRejected() throws Exception {
    assertEquals(
        401, mvc.perform(get("/api/appointments/1")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/appointments")
                    .session(admin)
                    .contentType("application/json")
                    .content(json.writeValueAsString(bookBody(start, staff, resource))))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void disabledAccountImmediatelyLosesExistingSession() throws Exception {
    var row = getAs(admin, "/lists/users?search=s" + suffix, 200).get("items").get(0);
    send(
        admin,
        put("/api/admin/users/" + accountId),
        Map.of(
            "username",
            row.get("username").asString(),
            "displayName",
            "Test",
            "roleId",
            row.get("roleId").asLong(),
            "departmentId",
            1,
            "enabled",
            false),
        200);
    getAs(stylist, "/auth/me", 401);
  }

  @Test
  void atomicClientImportRollsBackEarlierRows() throws Exception {
    String name = "ATOMIC-" + suffix;
    postAs(
        admin,
        "/master/customers/import",
        List.of(
            Map.of("name", name, "departmentId", 1, "enabled", true),
            Map.of("name", "", "departmentId", 1, "enabled", true)),
        400);
    assertEquals(0, getAs(admin, "/lists/customers?search=" + name, 200).get("items").size());
  }

  @Test
  void scopeCannotBeBypassedWithForeignDepartmentMaster() throws Exception {
    long dep =
        postAs(admin, "/admin/departments", Map.of("name", "Other " + suffix), 200)
            .get("id")
            .asLong();
    var name = "m" + suffix;
    user(name, "店长 / Manager", dep);
    var manager = login(name, PASSWORD);
    getAs(manager, "/appointments/" + book(), 403);
    var body = serviceBody("100", 30, 15);
    postAs(manager, "/master/services", body, 403);
  }

  @Test
  void currencyAndTimezoneCannotChangeAfterFirstBooking() throws Exception {
    book();
    for (var s : getAs(admin, "/lists/settings", 200).get("items"))
      if (Set.of("currency", "timezone").contains(s.get("code").asString()))
        send(
            admin,
            put("/api/admin/settings/" + s.get("id").asLong()),
            Map.of(
                "value", s.get("code").asString().equals("currency") ? "USD" : "America/New_York"),
            409);
  }

  @Test
  void unconfiguredMailIsRecordedWithoutPretendingItWasSent() throws Exception {
    long id = book();
    tx.executeWithoutResult(
        st -> {
          var worker = new MailWorker(db, clock, bookings, "", 587, "", "", "", true, false, true);
          worker.dispatch();
        });
    var messages = getAs(admin, "/lists/messages?size=100", 200).get("items");
    boolean found = false;
    for (var j : messages)
      if (j.get("appointmentId").asLong() == id) {
        assertEquals("NOT_CONFIGURED", j.get("status").asString());
        found = true;
      }
    assertTrue(found);
  }

  @Test
  void rescheduleSlotsExcludeOriginalAndKeepFrozenDuration() throws Exception {
    long id = book();
    send(admin, put("/api/master/services/" + service), serviceBody("200", 60, 15), 200);
    var rows =
        getAs(
            admin,
            "/slots?serviceId="
                + service
                + "&staffId="
                + staff
                + "&resourceId="
                + resource
                + "&date=2026-10-01&appointmentId="
                + id,
            200);
    JsonNode original = null;
    for (var x : rows) if (x.get("startsAt").asString().equals(start.toString())) original = x;
    assertNotNull(original);
    assertEquals(start.plusSeconds(1800).toString(), original.get("endsAt").asString());
    getAs(
        otherStylist,
        "/slots?serviceId="
            + service
            + "&staffId="
            + staff
            + "&resourceId="
            + resource
            + "&date=2026-10-01&appointmentId="
            + id,
        403);
  }

  @Test
  void concurrentPaymentsCannotUseSameRevisionTwice() throws Exception {
    long id = book();
    served(id);
    action(admin, id, "checkout", "discount", "0");
    var first = command(id, "amount", "40", "method", "CASH", "reference", "TEST-A");
    var second = command(id, "amount", "40", "method", "CASH", "reference", "TEST-B");
    var latch = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<Future<Integer>>();
      for (var body : List.of(first, second))
        tasks.add(
            executor.submit(
                () -> {
                  latch.await();
                  return mvc.perform(
                          post("/api/appointments/" + id + "/pay")
                              .session(admin)
                              .with(csrf())
                              .contentType("application/json")
                              .content(json.writeValueAsString(body)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      latch.countDown();
      var results = new ArrayList<Integer>();
      for (var f : tasks) results.add(f.get(10, TimeUnit.SECONDS));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    }
    assertEquals(0, new BigDecimal("40").compareTo(appointment(id).get("paid").decimalValue()));
    assertEquals(1, getAs(admin, "/appointments/" + id, 200).get("payments").size());
  }

  @Test
  void salonLocalBlockedTimesUseConfiguredTimezone() throws Exception {
    var row =
        postAs(
            admin,
            "/master/blocks",
            Map.of(
                "departmentId",
                1,
                "staffId",
                staff,
                "startsLocal",
                "2026-10-01T09:30",
                "endsLocal",
                "2026-10-01T10:30",
                "reason",
                "TEST leave"),
            200);
    assertEquals("2026-10-01T01:30:00Z", row.get("startsAt").asString());
    postAs(admin, "/appointments", bookBody(start, staff, resource), 409);
  }

  /** 仅隔离本测试的通知时点，不访问外部SMTP或真实邮箱。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void isolateMail(long id) {
    tx.executeWithoutResult(
        st -> {
          for (var j : db.all(MessageJob.class))
            if (!j.appointmentId.equals(id) && j.status.equals("QUEUED"))
              j.dueAt = clock.instant().plusSeconds(86400L * 500);
        });
  }

  JsonNode message(long id) throws Exception {
    for (var j : getAs(admin, "/lists/messages?size=100", 200).get("items"))
      if (j.get("appointmentId").asLong() == id) return j;
    throw new AssertionError("Missing message");
  }

  MailWorker worker(int port) {
    return new MailWorker(
        db, clock, bookings, "127.0.0.1", port, "", "", "test@example.invalid", false, false, true);
  }

  @Test
  void smtpAcceptancePersistsWithoutClaimingCustomerRead() throws Exception {
    long id = book();
    isolateMail(id);
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        var executor = Executors.newSingleThreadExecutor()) {
      server.setSoTimeout(5000);
      var received =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  socket.setSoTimeout(5000);
                  var input =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                  var out =
                      new PrintWriter(
                          new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                          true);
                  out.print("220 test SMTP\r\n");
                  out.flush();
                  var content = new StringBuilder();
                  String line;
                  while ((line = input.readLine()) != null) {
                    if (line.startsWith("EHLO") || line.startsWith("HELO"))
                      out.print("250 localhost\r\n");
                    else if (line.equals("DATA")) {
                      out.print("354 send\r\n");
                      out.flush();
                      while ((line = input.readLine()) != null && !line.equals("."))
                        content.append(line).append("\n");
                      out.print("250 accepted\r\n");
                    } else if (line.equals("QUIT")) {
                      out.print("221 bye\r\n");
                      out.flush();
                      break;
                    } else out.print("250 ok\r\n");
                    out.flush();
                  }
                  return content.toString();
                }
              });
      var w = worker(server.getLocalPort());
      tx.executeWithoutResult(st -> w.dispatch());
      assertTrue(received.get(5, TimeUnit.SECONDS).contains("@example.invalid"));
      var j = message(id);
      assertEquals("SENT", j.get("status").asString());
      assertEquals("SMTP_ACCEPTED", j.get("resultCode").asString());
      assertFalse(j.get("sentAt").isNull());
    }
  }

  @Test
  void smtpFailureRetriesThreeTimesThenStops() throws Exception {
    long id = book();
    isolateMail(id);
    int port;
    try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    var w = worker(port);
    for (int i = 1; i <= 3; i++) {
      tx.executeWithoutResult(st -> w.dispatch());
      var j = message(id);
      assertEquals(i, j.get("attempts").asInt());
      assertEquals(i == 3 ? "FAILED" : "QUEUED", j.get("status").asString());
      assertEquals("SMTP_ERROR", j.get("resultCode").asString());
      clock.now = clock.now.plusSeconds(300);
    }
    tx.executeWithoutResult(st -> w.dispatch());
    assertEquals(3, message(id).get("attempts").asInt());
  }

  @Test
  void noConsentAndStaleConfirmationAreNeverSent() throws Exception {
    long id = book();
    isolateMail(id);
    tx.executeWithoutResult(st -> db.get(Customer.class, customer).emailConsent = false);
    var w = worker(1);
    tx.executeWithoutResult(st -> w.dispatch());
    assertEquals("SKIPPED", message(id).get("status").asString());
    assertEquals(0, message(id).get("attempts").asInt());
    action(admin, id, "cancel", "note", "TEST cancellation");
    start = start.plusSeconds(3600);
    long other = book();
    isolateMail(other);
    clock.now = start;
    action(admin, other, "arrive");
    tx.executeWithoutResult(st -> w.dispatch());
    assertEquals("CANCELLED", message(other).get("status").asString());
  }

  @Test
  void directBookingApiRejectsAmbiguousAndClockJumpTimesAtomically() throws Exception {
    tx.executeWithoutResult(
        st ->
            db.query(SystemSetting.class, "from SystemSetting where code=?1", "timezone")
                    .getFirst()
                    .value =
                "America/New_York");
    try {
      var first =
          postAs(
              admin,
              "/appointments",
              bookBody(Instant.parse("2026-11-01T05:30:00Z"), staff, resource),
              400);
      assertEquals("AMBIGUOUS_LOCAL_TIME", first.get("code").asString());
      clock.now = Instant.parse("2026-03-01T01:00:00Z");
      var second =
          postAs(
              admin,
              "/appointments",
              bookBody(Instant.parse("2026-03-08T06:45:00Z"), staff, resource),
              400);
      assertEquals("AMBIGUOUS_LOCAL_TIME", second.get("code").asString());
      tx.executeWithoutResult(
          st ->
              assertTrue(
                  db.query(Appointment.class, "from Appointment where customerId=?1", customer)
                      .isEmpty()));
    } finally {
      tx.executeWithoutResult(
          st ->
              db.query(SystemSetting.class, "from SystemSetting where code=?1", "timezone")
                      .getFirst()
                      .value =
                  "Asia/Shanghai");
    }
  }

  private MockHttpSession client(String name) throws Exception {
    postAs(
        null,
        "/public/register",
        Map.of(
            "username",
            name,
            "password",
            PASSWORD,
            "name",
            "Test customer",
            "departmentId",
            1,
            "roleId",
            1,
            "emailConsent",
            false),
        200);
    return login(name, PASSWORD);
  }
}
