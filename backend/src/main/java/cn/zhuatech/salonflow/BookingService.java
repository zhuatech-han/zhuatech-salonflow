// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 预约可用时间、资源冲突、本人服务与原单收退款；写入按门店配置锁串行化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class BookingService {
  final Store db;
  final AccessService access;
  final CommandService commands;
  final Clock clock;

  public BookingService(Store db, AccessService access, CommandService commands, Clock clock) {
    this.db = db;
    this.access = access;
    this.commands = commands;
    this.clock = clock;
  }

  /** 业务时钟，不直接读取事务代理字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Instant now() {
    return clock.instant();
  }

  /** 门店统一时区，已有预约后不能变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ZoneId zone() {
    return ZoneId.of(setting("timezone"));
  }

  /** 读取受管理范围约束的内建业务参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }

  /** 取消和爽约释放预约容量，服务中持续阻塞直到真实完工，避免超时服务再接单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean reserved(Appointment o) {
    return Set.of("CONFIRMED", "ARRIVED", "IN_SERVICE").contains(o.status)
        || Set.of("SERVED", "COMPLETED").contains(o.status)
            && o.blockedUntil.isAfter(clock.instant());
  }

  /** 客户仅本人预约，员工仅被指派服务，管理角色按部门/全部范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Appointment o) {
    if (!access.visible(o.departmentId)) return false;
    var a = access.current();
    if (a.kind.equals("CUSTOMER"))
      return Objects.equals(db.get(Customer.class, o.customerId).accountId, a.id);
    return !access.role().scope.equals("ASSIGNED")
        || Objects.equals(db.get(StaffMember.class, o.staffId).accountId, a.id);
  }

  /** 返回预约列表，接口仍检查实时角色和账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Appointment> list() {
    access.require("appointment.read");
    return db.all(Appointment.class).stream().filter(this::visible).toList();
  }

  /** 预约完整原单、流水和事件；不会绕过客户或员工的数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var o = get(id);
    return Map.of(
        "appointment",
        o,
        "events",
        db.query(BookingEvent.class, "from BookingEvent where appointmentId=?1", id),
        "payments",
        db.query(PaymentEntry.class, "from PaymentEntry where appointmentId=?1", id));
  }

  /** 指定员工服务与资源的本地单日可用时段；搜索结果仅供提示，落单再加锁核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> slots(
      Long serviceId, Long staffId, Long resourceId, String date, Long appointmentId) {
    access.current();
    var s = db.get(SalonService.class, serviceId);
    access.department(s.departmentId);
    var day = LocalDate.parse(date);
    var today = clock.instant().atZone(zone()).toLocalDate();
    if (day.isBefore(today) || day.isAfter(today.plusDays(90)))
      throw new Problem(400, "BOOKING_WINDOW");
    var result = new ArrayList<Map<String, Object>>();
    var customer = ownCustomer();
    Appointment original = appointmentId == null ? null : get(appointmentId);
    if (original != null) {
      access.require(
          access.current().kind.equals("CUSTOMER") ? "customer.book" : "appointment.manage");
      state(original, "CONFIRMED");
      customerNotice(original);
      if (!original.serviceId.equals(serviceId)) throw new Problem(400, "INVALID_SERVICE");
    }
    int duration = original == null ? s.durationMinutes : original.durationMinutes;
    int buffer = original == null ? s.bufferMinutes : original.bufferMinutes;
    Long customerId =
        original != null ? original.customerId : customer == null ? null : customer.id;
    for (int minute = 0; minute < 1440; minute += 15) {
      try {
        var start = BookingPolicy.local(day, minute, zone());
        if (start.isBefore(clock.instant().plusSeconds(60L * lead()))) continue;
        validate(
            s,
            staffId,
            resourceId,
            customerId,
            start,
            start.plusSeconds(60L * (duration + buffer)),
            appointmentId);
        result.add(
            Map.of(
                "startsAt",
                start,
                "endsAt",
                start.plusSeconds(60L * duration),
                "label",
                String.format("%02d:%02d", minute / 60, minute % 60)));
      } catch (Problem ignored) {
        /* Invalid or occupied slots are omitted; mutation still returns the precise error. */
      }
    }
    return result;
  }

  /** 建立不可变服务价格和时長快照，自助客户身份由服务器取得，不能代他人预约。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Appointment create(Map<String, Object> v) {
    var a = access.current();
    access.require(a.kind.equals("CUSTOMER") ? "customer.book" : "appointment.manage");
    db.lock(Department.class, 1L);
    var c =
        a.kind.equals("CUSTOMER")
            ? ownCustomer()
            : db.get(Customer.class, BookingPolicy.id(v, "customerId"));
    if (c == null || !c.enabled) throw new Problem(400, "INVALID_CUSTOMER");
    access.department(c.departmentId);
    var command = commands.open("BOOK", v);
    if (!command.fresh()) return get(command.stamp().resultId);
    var s = db.get(SalonService.class, BookingPolicy.id(v, "serviceId"));
    var staff = BookingPolicy.id(v, "staffId");
    Long res = nullable(v, "resourceId");
    var start = BookingPolicy.instant(v.get("startsAt"));
    window(start);
    validate(
        s,
        staff,
        res,
        c.id,
        start,
        start.plusSeconds(60L * (s.durationMinutes + s.bufferMinutes)),
        null);
    if (!s.departmentId.equals(c.departmentId)) throw new Problem(400, "INVALID_CUSTOMER");
    var o = new Appointment();
    o.number =
        "SF-"
            + start.atZone(zone()).toLocalDate()
            + "-"
            + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    o.departmentId = s.departmentId;
    o.customerId = c.id;
    o.customerName = c.name;
    o.staffId = staff;
    o.staffName = db.get(StaffMember.class, staff).name;
    o.serviceId = s.id;
    o.serviceName = s.name;
    o.resourceId = res;
    o.resourceName = res == null ? "" : db.get(Resource.class, res).name;
    o.startsAt = start;
    o.durationMinutes = s.durationMinutes;
    o.bufferMinutes = s.bufferMinutes;
    o.endsAt = start.plusSeconds(s.durationMinutes * 60L);
    o.blockedUntil = o.endsAt.plusSeconds(s.bufferMinutes * 60L);
    o.price = s.price;
    o.total = s.price;
    o.note = BookingPolicy.optional(v.get("note"), 1000);
    o.createdAt = clock.instant();
    o.updatedAt = o.createdAt;
    db.save(o);
    command.stamp().resultId = o.id;
    event(o, "BOOKED", o.startsAt + " / " + o.staffName);
    notify(o, "CONFIRM", clock.instant());
    remind(o);
    return o;
  }

  /** 在修订号保护下改期、到店、本人服务和结账；管理员不能冒名执行员工服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void action(Long id, String action, Map<String, Object> v) {
    var a = access.current();
    String p =
        switch (action) {
          case "reschedule", "cancel" ->
              a.kind.equals("CUSTOMER") ? "customer.book" : "appointment.manage";
          case "arrive", "no-show", "reassign" -> "appointment.manage";
          case "start", "finish" -> "execute";
          case "checkout", "pay" -> "checkout";
          case "refund" -> "refund";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    access.require(p);
    db.lock(Department.class, 1L);
    var o = get(id);
    var command = commands.open("APPOINTMENT:" + id + ":" + action, v);
    if (!command.fresh()) return;
    long rev = BookingPolicy.integer(v.get("revision"), 0, Integer.MAX_VALUE);
    if (o.revision != rev) throw new Problem(409, "STALE_REVISION");
    String note = BookingPolicy.optional(v.get("note"), 1000);
    switch (action) {
      case "reschedule" -> {
        state(o, "CONFIRMED");
        customerNotice(o);
        var start = BookingPolicy.instant(v.get("startsAt"));
        window(start);
        long staff = BookingPolicy.id(v, "staffId");
        Long resource = nullable(v, "resourceId");
        var s =
            db.get(
                SalonService.class,
                o.serviceId); // original booked duration and price stay unchanged
        var old = o.startsAt;
        validate(
            s,
            staff,
            resource,
            o.customerId,
            start,
            start.plusSeconds(60L * (o.durationMinutes + o.bufferMinutes)),
            id);
        o.startsAt = start;
        o.endsAt = start.plusSeconds(o.durationMinutes * 60L);
        o.blockedUntil = o.endsAt.plusSeconds(o.bufferMinutes * 60L);
        o.staffId = staff;
        o.staffName = db.get(StaffMember.class, staff).name;
        o.resourceId = resource;
        o.resourceName = resource == null ? "" : db.get(Resource.class, resource).name;
        note = "From " + old + " to " + start + "; " + note;
      }
      case "cancel" -> {
        state(o, "CONFIRMED");
        customerNotice(o);
        if (note.isBlank()) throw new Problem(400, "REASON_REQUIRED");
        o.status = "CANCELLED";
      }
      case "no-show" -> {
        state(o, "CONFIRMED");
        if (clock.instant().isBefore(o.endsAt)) throw new Problem(409, "NOT_DUE");
        if (note.isBlank()) throw new Problem(400, "REASON_REQUIRED");
        o.status = "NO_SHOW";
      }
      case "arrive" -> {
        state(o, "CONFIRMED");
        if (clock.instant().isBefore(o.startsAt.minusSeconds(3600)))
          throw new Problem(409, "TOO_EARLY");
        if (clock.instant().isAfter(o.endsAt.plusSeconds(3600)))
          throw new Problem(409, "APPOINTMENT_EXPIRED");
        o.status = "ARRIVED";
      }
      case "reassign" -> {
        if (!Set.of("CONFIRMED", "ARRIVED").contains(o.status))
          throw new Problem(409, "INVALID_STATE");
        long staff = BookingPolicy.id(v, "staffId");
        validate(
            db.get(SalonService.class, o.serviceId),
            staff,
            o.resourceId,
            o.customerId,
            o.startsAt,
            o.blockedUntil,
            id);
        o.staffId = staff;
        o.staffName = db.get(StaffMember.class, staff).name;
        if (note.isBlank()) throw new Problem(400, "REASON_REQUIRED");
      }
      case "start" -> {
        state(o, "ARRIVED");
        performer(o);
        if (clock.instant().isBefore(o.startsAt.minusSeconds(900)))
          throw new Problem(409, "TOO_EARLY");
        for (var other : db.all(Appointment.class))
          if (!other.id.equals(id)
              && other.status.equals("IN_SERVICE")
              && (other.staffId.equals(o.staffId)
                  || o.resourceId != null && Objects.equals(other.resourceId, o.resourceId)))
            throw new Problem(409, "SERVICE_IN_PROGRESS");
        o.status = "IN_SERVICE";
        o.serviceStartedAt = clock.instant();
      }
      case "finish" -> {
        state(o, "IN_SERVICE");
        performer(o);
        if (note.isBlank()) throw new Problem(400, "SERVICE_NOTE_REQUIRED");
        o.serviceNote = note;
        o.serviceFinishedAt = clock.instant();
        o.status = "SERVED";
      }
      case "checkout" -> {
        state(o, "SERVED");
        if (o.checkedOut) throw new Problem(409, "ALREADY_CHECKED_OUT");
        var discount = BookingPolicy.money(v.get("discount"), false);
        if (discount.compareTo(o.price) > 0) throw new Problem(400, "DISCOUNT_EXCEEDS_PRICE");
        if (discount.signum() > 0 && note.isBlank()) throw new Problem(400, "REASON_REQUIRED");
        o.discount = discount;
        o.total = o.price.subtract(discount);
        o.checkedOut = true;
        complete(o);
      }
      case "pay" -> {
        state(o, "SERVED");
        if (!o.checkedOut) throw new Problem(409, "CHECKOUT_REQUIRED");
        var amount = BookingPolicy.money(v.get("amount"), true);
        if (o.paid.add(amount).compareTo(o.total) > 0)
          throw new Problem(409, "PAYMENT_EXCEEDS_DUE");
        var method = required(v, "method", 60);
        if (db.query(
                DictionaryEntry.class,
                "from DictionaryEntry where type='payment_method' and code=?1 and enabled=true",
                method)
            .isEmpty()) throw new Problem(400, "INVALID_PAYMENT_METHOD");
        payment(o, null, "PAYMENT", amount, method, required(v, "reference", 200), "");
        o.paid = o.paid.add(amount);
        complete(o);
      }
      case "refund" -> {
        state(o, "COMPLETED");
        var source = db.get(PaymentEntry.class, BookingPolicy.id(v, "sourceId"));
        if (!source.appointmentId.equals(o.id) || !source.kind.equals("PAYMENT"))
          throw new Problem(400, "INVALID_PAYMENT_SOURCE");
        var amount = BookingPolicy.money(v.get("amount"), true);
        var prior =
            db.query(PaymentEntry.class, "from PaymentEntry where sourceId=?1", source.id).stream()
                .map(x -> x.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (prior.add(amount).compareTo(source.amount) > 0)
          throw new Problem(409, "REFUND_EXCEEDS_PAYMENT");
        payment(
            o,
            source.id,
            "REFUND",
            amount,
            source.method,
            required(v, "reference", 200),
            required(v, "note", 1000));
        o.refunded = o.refunded.add(amount);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    o.revision++;
    o.updatedAt = clock.instant();
    event(o, action.toUpperCase(Locale.ROOT), note);
    if (action.equals("reschedule")) {
      cancelMessages(o);
      notify(o, "RESCHEDULE", clock.instant());
      remind(o);
    }
    if (action.equals("cancel")) {
      cancelMessages(o);
      notify(o, "CANCEL", clock.instant());
    }
  }

  /** 核对员工排班与休息，跨午夜服务不在首版范围，夏令时边界明确拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void inShift(Long staff, Instant start, Instant end) {
    BookingPolicy.unambiguousInterval(start, end, zone());
    var day = start.atZone(zone()).toLocalDate();
    if (end.atZone(zone()).toLocalDate().isAfter(day)
        && !end.equals(BookingPolicy.local(day, 1440, zone())))
      throw new Problem(409, "OUTSIDE_SHIFT");
    var shifts =
        db.query(
            WorkShift.class,
            "from WorkShift where staffId=?1 and weekday=?2",
            staff,
            start.atZone(zone()).getDayOfWeek().getValue());
    if (shifts.isEmpty()) throw new Problem(409, "OUTSIDE_SHIFT");
    var s = shifts.getFirst();
    var a = BookingPolicy.local(day, s.startMinute, zone());
    var b = BookingPolicy.local(day, s.endMinute, zone());
    if (start.isBefore(a) || end.isAfter(b)) throw new Problem(409, "OUTSIDE_SHIFT");
    if (s.breakEnd > s.breakStart
        && BookingPolicy.overlap(
            start,
            end,
            BookingPolicy.local(day, s.breakStart, zone()),
            BookingPolicy.local(day, s.breakEnd, zone()))) throw new Problem(409, "STAFF_BREAK");
  }

  /** 员工、资源、客户相同区间冲突校验，进行中服务不会在预定结束时自动释放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void validate(
      SalonService s,
      Long staffId,
      Long resourceId,
      Long customerId,
      Instant start,
      Instant end,
      Long except) {
    var m = db.get(StaffMember.class, staffId);
    var account = db.get(Account.class, m.accountId);
    if (!s.enabled
        || !db.get(ServiceCategory.class, s.categoryId).enabled
        || !m.enabled
        || !account.enabled
        || !account.kind.equals("STAFF")
        || !db.get(AccessRole.class, account.roleId).permissions.contains("execute")
        || !m.departmentId.equals(s.departmentId)
        || !m.services.contains(s.id)) throw new Problem(409, "STAFF_SERVICE_UNAVAILABLE");
    if (s.resourceRequired && resourceId == null) throw new Problem(400, "RESOURCE_REQUIRED");
    if (resourceId != null) {
      var r = db.get(Resource.class, resourceId);
      if (!r.enabled || !r.departmentId.equals(s.departmentId))
        throw new Problem(400, "INVALID_RESOURCE");
    }
    inShift(staffId, start, end);
    for (var x : db.all(BlockedTime.class))
      if ((Objects.equals(x.staffId, staffId)
              || resourceId != null && Objects.equals(x.resourceId, resourceId))
          && BookingPolicy.overlap(start, end, x.startsAt, x.endsAt))
        throw new Problem(409, "BLOCKED_TIME");
    for (var o : db.all(Appointment.class)) {
      if (Objects.equals(o.id, except)) continue;
      boolean same =
          o.staffId.equals(staffId)
              || resourceId != null && Objects.equals(o.resourceId, resourceId)
              || customerId != null && Objects.equals(o.customerId, customerId);
      if (same
          && ((reserved(o) && BookingPolicy.overlap(start, end, o.startsAt, o.blockedUntil))
              || (o.status.equals("IN_SERVICE") && o.startsAt.isBefore(end))))
        throw new Problem(409, "SLOT_TAKEN");
    }
  }

  /** 当前自助客户资料，非客户账号返回null。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Customer ownCustomer() {
    if (!access.current().kind.equals("CUSTOMER")) return null;
    var c = db.query(Customer.class, "from Customer where accountId=?1", access.current().id);
    if (c.isEmpty()) throw new Problem(403, "INVALID_CUSTOMER");
    return c.getFirst();
  }

  private int lead() {
    return access.current().kind.equals("CUSTOMER")
        ? Integer.parseInt(setting("bookingLeadMinutes"))
        : 0;
  }

  private void window(Instant start) {
    if (start.isBefore(clock.instant().plusSeconds(60L * lead()))
        || start.isAfter(clock.instant().plus(Duration.ofDays(90)))
        || start.atZone(zone()).getMinute() % 15 != 0) throw new Problem(400, "BOOKING_WINDOW");
  }

  private void customerNotice(Appointment o) {
    if (access.current().kind.equals("CUSTOMER")
        && clock
            .instant()
            .plusSeconds(60L * Integer.parseInt(setting("cancelLeadMinutes")))
            .isAfter(o.startsAt)) throw new Problem(409, "CANCELLATION_NOTICE");
  }

  private void performer(Appointment o) {
    var m = db.get(StaffMember.class, o.staffId);
    if (!m.enabled || !Objects.equals(m.accountId, access.current().id))
      throw new Problem(403, "NOT_ASSIGNED_STYLIST");
  }

  private void state(Appointment o, String expected) {
    if (!o.status.equals(expected)) throw new Problem(409, "INVALID_STATE");
  }

  private Appointment get(Long id) {
    access.require("appointment.read");
    var o = db.get(Appointment.class, id);
    if (!visible(o)) throw new Problem(403, "OUT_OF_SCOPE");
    return o;
  }

  private void complete(Appointment o) {
    if (o.paid.compareTo(o.total) == 0) {
      o.status = "COMPLETED";
      o.completedAt = clock.instant();
    }
  }

  private PaymentEntry payment(
      Appointment o,
      Long source,
      String kind,
      BigDecimal amount,
      String method,
      String reference,
      String reason) {
    var p = new PaymentEntry();
    p.appointmentId = o.id;
    p.departmentId = o.departmentId;
    p.sourceId = source;
    p.kind = kind;
    p.amount = amount;
    p.method = method;
    p.reference = reference;
    p.reason = reason;
    p.actorId = access.current().id;
    p.createdAt = clock.instant();
    return db.save(p);
  }

  private void event(Appointment o, String kind, String note) {
    var e = new BookingEvent();
    e.appointmentId = o.id;
    e.kind = kind;
    e.note = note;
    e.actorId = access.current().id;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit("APPOINTMENT_" + kind, o.id, o.departmentId);
  }

  private void notify(Appointment o, String kind, Instant due) {
    var j = new MessageJob();
    j.appointmentId = o.id;
    j.kind = kind;
    j.revision = o.revision;
    j.dueAt = due;
    db.save(j);
  }

  private void remind(Appointment o) {
    var due = o.startsAt.minus(Duration.ofHours(24));
    if (due.isAfter(clock.instant())) notify(o, "REMINDER", due);
  }

  private void cancelMessages(Appointment o) {
    db.query(MessageJob.class, "from MessageJob where appointmentId=?1 and status='QUEUED'", o.id)
        .forEach(j -> j.status = "CANCELLED");
  }

  private Long nullable(Map<String, Object> v, String key) {
    return v.get(key) == null || String.valueOf(v.get(key)).isBlank()
        ? null
        : BookingPolicy.id(v, key);
  }

  private String required(Map<String, Object> v, String key, int max) {
    return AdminService.text(BookingPolicy.optional(v.get(key), max), max);
  }
}
