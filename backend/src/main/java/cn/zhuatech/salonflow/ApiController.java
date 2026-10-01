// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 双端预约、经营档案及后台管理API，所有数据读写按实时权限检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final SalonServiceManager masters;
  final BookingService bookings;
  final MailWorker mail;

  public ApiController(
      Store db,
      AccessService access,
      AdminService admin,
      SalonServiceManager masters,
      BookingService bookings,
      MailWorker mail) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.masters = masters;
    this.bookings = bookings;
    this.mail = mail;
  }

  /** 客户目录不返回联系人、员工账号、排班或系统权限清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  @Transactional(readOnly = true)
  public Object catalog() {
    var a = access.current();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("settings", db.all(SystemSetting.class));
    r.put(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList());
    r.put(
        "services",
        db.all(SalonService.class).stream()
            .filter(s -> access.visible(s.departmentId))
            .filter(
                s ->
                    !a.kind.equals("CUSTOMER")
                        || s.enabled && db.get(ServiceCategory.class, s.categoryId).enabled)
            .toList());
    r.put(
        "categories",
        db.all(ServiceCategory.class).stream()
            .filter(c -> access.visible(c.departmentId))
            .toList());
    r.put(
        "resources",
        db.all(Resource.class).stream().filter(x -> access.visible(x.departmentId)).toList());
    r.put(
        "staff",
        db.all(StaffMember.class).stream()
            .filter(x -> access.visible(x.departmentId))
            .filter(
                x ->
                    !a.kind.equals("CUSTOMER")
                        || x.enabled && db.get(Account.class, x.accountId).enabled)
            .map(
                x -> {
                  if (!a.kind.equals("CUSTOMER")) return (Object) x;
                  return Map.of(
                      "id",
                      x.id,
                      "name",
                      x.name,
                      "departmentId",
                      x.departmentId,
                      "services",
                      x.services,
                      "enabled",
                      x.enabled);
                })
            .toList());
    if (a.kind.equals("CUSTOMER")) r.put("customer", bookings.ownCustomer());
    if (can("customer.write")) r.put("customers", masters.list("customers"));
    if (can("checkout")) r.put("dictionaries", db.all(DictionaryEntry.class));
    if (can("master.write"))
      r.put(
          "accounts",
          db.all(Account.class).stream()
              .filter(x -> x.kind.equals("STAFF") && access.visible(x.departmentId))
              .toList());
    if (can("admin")) {
      r.put("roles", admin.list("roles"));
      r.put("permissions", admin.list("permissions"));
    }
    return r;
  }

  /** 按名称、时间和状态筛选与分页，最多一百行，每种资源先验证权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lists/{resource}")
  @Transactional(readOnly = true)
  public Object lists(
      @PathVariable String resource,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String date,
      @RequestParam(defaultValue = "id") String sort,
      @RequestParam(defaultValue = "true") boolean desc,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0
        || size < 1
        || size > 100
        || search.length() > 200
        || !Set.of("id", "name", "number", "startsAt", "createdAt").contains(sort))
      throw new Problem(400, "INVALID_PAGE");
    var q = search.toLowerCase(Locale.ROOT);
    var rows =
        rows(resource).stream()
            .filter(
                x ->
                    List.of(
                            "name",
                            "number",
                            "customerName",
                            "staffName",
                            "serviceName",
                            "username",
                            "displayName",
                            "reference",
                            "reason",
                            "actor",
                            "action",
                            "code",
                            "kind")
                        .stream()
                        .map(k -> String.valueOf(field(x, k)))
                        .collect(Collectors.joining(" "))
                        .toLowerCase(Locale.ROOT)
                        .contains(q))
            .filter(
                x ->
                    status.isBlank()
                        || status.equals(field(x, "status"))
                        || status.equals(field(x, "kind")))
            .filter(
                x ->
                    date.isBlank()
                        || !(x instanceof Appointment o)
                        || o.startsAt
                            .atZone(bookings.zone())
                            .toLocalDate()
                            .equals(LocalDate.parse(date)))
            .sorted(
                (a, b) -> {
                  int n =
                      sort.equals("id")
                          ? Long.compare(
                              ((Number) field(a, "id")).longValue(),
                              ((Number) field(b, "id")).longValue())
                          : String.valueOf(field(a, sort))
                              .compareTo(String.valueOf(field(b, sort)));
                  return desc ? -n : n;
                })
            .toList();
    return Map.of(
        "items",
        rows.stream().skip((long) page * size).limit(size).toList(),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 经营档案新建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/{type}")
  public Object createMaster(@PathVariable String type, @RequestBody Map<String, Object> v) {
    return masters.save(type, null, v);
  }

  /** 更新受引用约束的档案和排班。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/{type}/{id}")
  public Object updateMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> v) {
    return masters.save(type, id, v);
  }

  /** 删除未被历史引用的档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Object deleteMaster(@PathVariable String type, @PathVariable Long id) {
    masters.delete(type, id);
    return Map.of("ok", true);
  }

  /** 原子导入客户档案，明确受权限和数量限制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/customers/import")
  public Object importCustomers(@RequestBody List<Map<String, Object>> v) {
    return masters.importCustomers(v);
  }

  /** 新增系统管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 系统管理更新，保护最后管理员和已关联账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 管理删除受引用和内建资源保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }

  /** 预约查询和落单使用同一真实可用性规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/slots")
  public Object slots(
      @RequestParam Long serviceId,
      @RequestParam Long staffId,
      @RequestParam(required = false) Long resourceId,
      @RequestParam String date,
      @RequestParam(required = false) Long appointmentId) {
    return bookings.slots(serviceId, staffId, resourceId, date, appointmentId);
  }

  /** 后台或自助客户预约，重复网络请求不会重复创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/appointments")
  public Object book(@RequestBody Map<String, Object> v) {
    return bookings.create(v);
  }

  /** 原单详情与履历按本人/部门范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/appointments/{id}")
  public Object detail(@PathVariable Long id) {
    return bookings.detail(id);
  }

  /** 改期、接待、服务、结账和退款均真实校验状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/appointments/{id}/{action}")
  public Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    bookings.action(id, action, v);
    return Map.of("ok", true);
  }

  /** 本人客户更新联系方式与邮件同意，不接收角色或部门输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/my-profile")
  @Transactional
  public Object profile(@RequestBody Map<String, Object> v) {
    access.require("customer.book");
    db.lock(Department.class, 1L);
    var c = bookings.ownCustomer();
    if (c == null) throw new Problem(403, "FORBIDDEN");
    c.phone = BookingPolicy.optional(v.get("phone"), 60);
    c.email = BookingPolicy.optional(v.get("email"), 200);
    if (!c.email.isEmpty() && !c.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new Problem(400, "INVALID_EMAIL");
    c.emailConsent = Boolean.TRUE.equals(v.get("emailConsent"));
    access.audit("CUSTOMER_PROFILE", c.id, c.departmentId);
    return c;
  }

  /** 明确的通知失败重试，不把模拟发送记成成功。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/messages/{id}/retry")
  public Object retry(@PathVariable Long id) {
    mail.retry(id, access);
    return Map.of("ok", true);
  }

  /** 门店当天或客户本人预约仪表盘，跨部门与他人预约不进入计数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var date = bookings.now().atZone(bookings.zone()).toLocalDate();
    var jobs = bookings.list();
    var today =
        jobs.stream()
            .filter(o -> o.startsAt.atZone(bookings.zone()).toLocalDate().equals(date))
            .toList();
    return Map.of(
        "date",
        date,
        "counts",
        today.stream().collect(Collectors.groupingBy(o -> o.status, Collectors.counting())),
        "today",
        today,
        "upcoming",
        jobs.stream()
            .filter(o -> o.status.equals("CONFIRMED") && o.startsAt.isAfter(bookings.now()))
            .sorted(Comparator.comparing(o -> o.startsAt))
            .limit(10)
            .toList());
  }

  /**
   * 以原始付款日统计实际收款、退款和净收款；业务完结日统计服务销售，不把预约价格当收入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  @GetMapping("/reports")
  @Transactional(readOnly = true)
  public Map<String, Object> reports(@RequestParam String from, @RequestParam String to) {
    access.require("report");
    var start = LocalDate.parse(from);
    var end = LocalDate.parse(to);
    if (end.isBefore(start)
        || Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > 366)
      throw new Problem(400, "INVALID_DATE");
    var ledger =
        db.all(PaymentEntry.class).stream()
            .filter(p -> access.visible(p.departmentId))
            .filter(p -> inDate(p.createdAt, start, end))
            .toList();
    var jobs =
        bookings.list().stream()
            .filter(o -> o.status.equals("COMPLETED") && inDate(o.completedAt, start, end))
            .toList();
    var receipts = sum(ledger, "PAYMENT");
    var refunds = sum(ledger, "REFUND");
    return Map.of(
        "items",
        ledger,
        "completed",
        jobs.size(),
        "serviceSales",
        jobs.stream().map(o -> o.total).reduce(BigDecimal.ZERO, BigDecimal::add),
        "receipts",
        receipts,
        "refunds",
        refunds,
        "netReceipts",
        receipts.subtract(refunds),
        "outstanding",
        bookings.list().stream()
            .filter(o -> o.checkedOut && o.status.equals("SERVED"))
            .map(o -> o.total.subtract(o.paid))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  /** 相同业务口径CSV导出，无联系方式广告或隐藏字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping(value = "/reports.csv", produces = "text/csv")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> csv(@RequestParam String from, @RequestParam String to) {
    var r = reports(from, to);
    var keys =
        List.of(
            "id", "appointmentId", "kind", "amount", "method", "reference", "reason", "createdAt");
    var s = new StringBuilder("\uFEFF" + String.join(",", keys) + "\r\n");
    for (var p : (List<?>) r.get("items"))
      s.append(
              keys.stream()
                  .map(k -> BookingPolicy.csv(field(p, k)))
                  .collect(Collectors.joining(",")))
          .append("\r\n");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=salon-payments.csv")
        .body(s.toString().getBytes(StandardCharsets.UTF_8));
  }

  private List<?> rows(String resource) {
    return switch (resource) {
      case "appointments" -> bookings.list();
      case "services", "categories", "staff", "resources", "customers", "shifts", "blocks" ->
          masters.list(resource);
      case "payments" -> {
        access.require("checkout");
        yield db.all(PaymentEntry.class).stream()
            .filter(p -> access.visible(p.departmentId))
            .toList();
      }
      case "messages" -> {
        access.require("messages");
        yield db.all(MessageJob.class).stream()
            .filter(j -> access.visible(db.get(Appointment.class, j.appointmentId).departmentId))
            .toList();
      }
      case "audit" -> {
        access.require("audit");
        yield db.all(AuditEvent.class).stream()
            .filter(x -> access.visible(x.departmentId))
            .toList();
      }
      default -> admin.list(resource);
    };
  }

  private boolean can(String p) {
    return access.role().permissions.contains(p);
  }

  private boolean inDate(Instant t, LocalDate start, LocalDate end) {
    var d = t.atZone(bookings.zone()).toLocalDate();
    return !d.isBefore(start) && !d.isAfter(end);
  }

  private BigDecimal sum(List<PaymentEntry> ps, String kind) {
    return ps.stream()
        .filter(p -> p.kind.equals(kind))
        .map(p -> p.amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Object field(Object x, String key) {
    try {
      return x.getClass().getField(key).get(x);
    } catch (ReflectiveOperationException e) {
      return "";
    }
  }
}
