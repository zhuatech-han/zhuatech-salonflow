// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护门店项目、员工技能、客户和排班，所有引用都在同门店范围校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class SalonServiceManager {
  final Store db;
  final AccessService access;
  final BookingService bookings;

  public SalonServiceManager(Store db, AccessService access, BookingService bookings) {
    this.db = db;
    this.access = access;
    this.bookings = bookings;
  }

  /** 有权限的经营档案列表，不向客户端披露内部客户联系方式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> list(String type) {
    access.require(
        Set.of("shifts", "blocks").contains(type)
            ? "schedule"
            : type.equals("customers") ? "customer.write" : "master.read");
    return db.all(type(type)).stream()
        .filter(x -> access.visible(department(x)))
        .filter(
            x ->
                !"ASSIGNED".equals(access.role().scope)
                    || !(x instanceof StaffMember m)
                    || Objects.equals(m.accountId, access.current().id))
        .toList();
  }

  /**
   * 保存服务与排班，已有预约的时间快照不随档案变化，不能停用仍有待服务预约的员工和资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  public Object save(String type, Long id, Map<String, Object> v) {
    access.require(
        Set.of("shifts", "blocks").contains(type)
            ? "schedule"
            : type.equals("customers") ? "customer.write" : "master.write");
    db.lock(Department.class, 1L);
    Object row = id == null ? fresh(type) : db.get(type(type), id);
    if (id != null) access.department(department(row));
    long dept = id == null ? BookingPolicy.id(v, "departmentId") : department(row);
    db.get(Department.class, dept);
    access.department(dept);
    if (id != null
        && v.containsKey("departmentId")
        && !Objects.equals(BookingPolicy.id(v, "departmentId"), dept))
      throw new Problem(409, "DEPARTMENT_LOCKED");
    switch (type) {
      case "categories" -> {
        var x = (ServiceCategory) row;
        x.name = text(v, "name", 120);
        x.departmentId = dept;
        x.enabled = enabled(v);
      }
      case "services" -> {
        var x = (SalonService) row;
        var c = db.get(ServiceCategory.class, BookingPolicy.id(v, "categoryId"));
        if (!c.enabled || !c.departmentId.equals(dept)) throw new Problem(400, "INVALID_CATEGORY");
        x.code = code(v);
        x.name = text(v, "name", 120);
        x.departmentId = dept;
        x.categoryId = c.id;
        x.price = BookingPolicy.money(v.get("price"), false);
        x.durationMinutes = BookingPolicy.integer(v.get("durationMinutes"), 15, 480);
        x.bufferMinutes = BookingPolicy.integer(v.get("bufferMinutes"), 0, 120);
        x.resourceRequired = Boolean.TRUE.equals(v.get("resourceRequired"));
        x.enabled = enabled(v);
      }
      case "resources" -> {
        var x = (Resource) row;
        if (id != null && !enabled(v) && activeResource(id))
          throw new Problem(409, "ACTIVE_APPOINTMENTS");
        x.code = code(v);
        x.name = text(v, "name", 120);
        x.departmentId = dept;
        x.enabled = enabled(v);
      }
      case "staff" -> {
        var x = (StaffMember) row;
        var a = db.get(Account.class, BookingPolicy.id(v, "accountId"));
        if (id != null && !x.accountId.equals(a.id)) throw new Problem(409, "ACCOUNT_LINKED");
        if (!a.enabled
            || !a.departmentId.equals(dept)
            || !a.kind.equals("STAFF")
            || !db.get(AccessRole.class, a.roleId).permissions.contains("execute"))
          throw new Problem(400, "INVALID_STYLIST");
        var ids = v.get("services");
        if (!(ids instanceof List<?> values) || values.isEmpty() || values.size() > 100)
          throw new Problem(400, "INVALID_SERVICES");
        var set = new HashSet<Long>();
        for (var n : values) {
          var s = db.get(SalonService.class, BookingPolicy.id(Map.of("id", n), "id"));
          if (!s.departmentId.equals(dept)) throw new Problem(400, "INVALID_SERVICES");
          set.add(s.id);
        }
        if (id != null
            && db.all(Appointment.class).stream()
                .anyMatch(
                    o ->
                        o.staffId.equals(id)
                            && bookings.reserved(o)
                            && (!enabled(v) || !set.contains(o.serviceId))))
          throw new Problem(409, "ACTIVE_APPOINTMENTS");
        x.accountId = a.id;
        x.name = text(v, "name", 120);
        x.departmentId = dept;
        x.enabled = enabled(v);
        x.services = set;
      }
      case "customers" -> {
        var x = (Customer) row;
        x.name = text(v, "name", 120);
        x.departmentId = dept;
        x.email = BookingPolicy.optional(v.get("email"), 200);
        if (!x.email.isEmpty() && !x.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
          throw new Problem(400, "INVALID_EMAIL");
        x.phone = BookingPolicy.optional(v.get("phone"), 60);
        x.note = BookingPolicy.optional(v.get("note"), 1000);
        x.emailConsent = Boolean.TRUE.equals(v.get("emailConsent"));
        x.enabled = enabled(v);
      }
      case "shifts" -> {
        var x = (WorkShift) row;
        var m = db.get(StaffMember.class, BookingPolicy.id(v, "staffId"));
        if (!m.departmentId.equals(dept) || id != null && !x.staffId.equals(m.id))
          throw new Problem(400, "INVALID_STYLIST");
        x.staffId = m.id;
        x.weekday = BookingPolicy.integer(v.get("weekday"), 1, 7);
        x.startMinute = BookingPolicy.integer(v.get("startMinute"), 0, 1439);
        x.endMinute = BookingPolicy.integer(v.get("endMinute"), 1, 1440);
        x.breakStart = BookingPolicy.integer(v.getOrDefault("breakStart", 0), 0, 1440);
        x.breakEnd = BookingPolicy.integer(v.getOrDefault("breakEnd", 0), 0, 1440);
        if (x.endMinute <= x.startMinute
            || ((x.breakStart != 0 || x.breakEnd != 0)
                && (x.breakStart < x.startMinute
                    || x.breakEnd > x.endMinute
                    || x.breakEnd <= x.breakStart))) throw new Problem(400, "INVALID_SHIFT");
        if (id == null) db.save(row);
        db.flush();
        for (var o : db.all(Appointment.class))
          if (o.staffId.equals(m.id) && bookings.reserved(o) && o.startsAt.isAfter(bookings.now()))
            bookings.inShift(m.id, o.startsAt, o.blockedUntil);
      }
      case "blocks" -> {
        var x = (BlockedTime) row;
        Long staff = nullableId(v, "staffId"), res = nullableId(v, "resourceId");
        if ((staff == null) == (res == null)) throw new Problem(400, "CHOOSE_STAFF_OR_RESOURCE");
        if (staff != null && !db.get(StaffMember.class, staff).departmentId.equals(dept)
            || res != null && !db.get(Resource.class, res).departmentId.equals(dept))
          throw new Problem(400, "OUT_OF_SCOPE");
        x.departmentId = dept;
        x.staffId = staff;
        x.resourceId = res;
        x.startsAt =
            v.containsKey("startsLocal")
                ? BookingPolicy.localInput(v.get("startsLocal"), bookings.zone())
                : BookingPolicy.instant(v.get("startsAt"));
        x.endsAt =
            v.containsKey("endsLocal")
                ? BookingPolicy.localInput(v.get("endsLocal"), bookings.zone())
                : BookingPolicy.instant(v.get("endsAt"));
        x.reason = text(v, "reason", 1000);
        if (!x.endsAt.isAfter(x.startsAt) || Duration.between(x.startsAt, x.endsAt).toDays() > 31)
          throw new Problem(400, "INVALID_TIME");
        for (var o : db.all(Appointment.class))
          if (bookings.reserved(o)
              && (Objects.equals(o.staffId, staff)
                  || res != null && Objects.equals(o.resourceId, res))
              && BookingPolicy.overlap(x.startsAt, x.endsAt, o.startsAt, o.blockedUntil))
            throw new Problem(409, "ACTIVE_APPOINTMENTS");
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    if (id == null && !type.equals("shifts")) db.save(row);
    access.audit("MASTER_SAVE_" + type, fieldId(row), dept);
    return row;
  }

  /** 批量客户导入同事务校验，失败时全部回滚。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object importCustomers(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty() || rows.size() > 500)
      throw new Problem(400, "INVALID_IMPORT");
    return rows.stream().map(v -> save("customers", null, v)).toList();
  }

  /** 删除未引用档案或未来排班前先检查已有预约；外键保护历史客户与服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    access.require(
        Set.of("shifts", "blocks").contains(type)
            ? "schedule"
            : type.equals("customers") ? "customer.write" : "master.write");
    db.lock(Department.class, 1L);
    var x = db.get(type(type), id);
    long dept = department(x);
    access.department(dept);
    if (x instanceof WorkShift w
        && db.all(Appointment.class).stream()
            .anyMatch(
                o ->
                    o.staffId.equals(w.staffId)
                        && bookings.reserved(o)
                        && o.startsAt.isAfter(bookings.now())
                        && o.startsAt.atZone(bookings.zone()).getDayOfWeek().getValue()
                            == w.weekday)) throw new Problem(409, "ACTIVE_APPOINTMENTS");
    if (x instanceof Customer c && c.accountId != null) throw new Problem(409, "ACCOUNT_LINKED");
    access.audit("MASTER_DELETE_" + type, id, dept);
    db.delete(x);
  }

  private boolean activeResource(Long id) {
    return db.all(Appointment.class).stream()
        .anyMatch(o -> Objects.equals(o.resourceId, id) && bookings.reserved(o));
  }

  private Long nullableId(Map<String, Object> v, String key) {
    return v.get(key) == null || String.valueOf(v.get(key)).isBlank()
        ? null
        : BookingPolicy.id(v, key);
  }

  private boolean enabled(Map<String, Object> v) {
    return Boolean.TRUE.equals(v.get("enabled"));
  }

  private String code(Map<String, Object> v) {
    var s = text(v, "code", 60);
    if (!s.matches("[A-Za-z0-9_.-]{1,60}")) throw new Problem(400, "INVALID_CODE");
    return s;
  }

  private String text(Map<String, Object> v, String k, int max) {
    return AdminService.text(BookingPolicy.optional(v.get(k), max), max);
  }

  private long fieldId(Object x) {
    try {
      return (Long) x.getClass().getField("id").get(x);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private long department(Object x) {
    if (x instanceof WorkShift w) return db.get(StaffMember.class, w.staffId).departmentId;
    try {
      return (Long) x.getClass().getField("departmentId").get(x);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object fresh(String t) {
    return switch (t) {
      case "categories" -> new ServiceCategory();
      case "services" -> new SalonService();
      case "resources" -> new Resource();
      case "staff" -> new StaffMember();
      case "customers" -> new Customer();
      case "shifts" -> new WorkShift();
      case "blocks" -> new BlockedTime();
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  private Class<?> type(String t) {
    return switch (t) {
      case "categories" -> ServiceCategory.class;
      case "services" -> SalonService.class;
      case "resources" -> Resource.class;
      case "staff" -> StaffMember.class;
      case "customers" -> Customer.class;
      case "shifts" -> WorkShift.class;
      case "blocks" -> BlockedTime.class;
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }
}
