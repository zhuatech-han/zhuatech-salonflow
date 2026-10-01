// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 门店公开服务及有限客户注册，不返回员工账号、客户数据或管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api/public")
public class PublicController {
  final Store db;
  final BookingService bookings;
  final BCryptPasswordEncoder encoder;
  final Clock clock;
  final Map<String, List<Instant>> registrations = new LinkedHashMap<>();

  public PublicController(
      Store db, BookingService bookings, BCryptPasswordEncoder encoder, Clock clock) {
    this.db = db;
    this.bookings = bookings;
    this.encoder = encoder;
    this.clock = clock;
  }

  /** 对外展示当前门店和服务价格，不宣称自动付款、会员卡或消息已接通。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  @Transactional(readOnly = true)
  public Object catalog() {
    return Map.of(
        "departments",
        db.all(Department.class),
        "services",
        db.all(SalonService.class).stream()
            .filter(s -> s.enabled && db.get(ServiceCategory.class, s.categoryId).enabled)
            .map(
                s ->
                    Map.of(
                        "id",
                        s.id,
                        "name",
                        s.name,
                        "departmentId",
                        s.departmentId,
                        "durationMinutes",
                        s.durationMinutes,
                        "price",
                        s.price))
            .toList(),
        "companyName",
        bookings.setting("companyName"),
        "currency",
        bookings.setting("currency"),
        "timezone",
        bookings.setting("timezone"),
        "registration",
        bookings.setting("publicRegistration").equals("true"));
  }

  /**
   * 仅创建本店客户固定角色；强密码、CSRF和每IP每小时十次有界限制，不接受角色输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  @PostMapping("/register")
  @Transactional
  public synchronized Object register(
      @RequestBody Map<String, Object> v, HttpServletRequest request) {
    if (!bookings.setting("publicRegistration").equals("true"))
      throw new Problem(403, "REGISTRATION_DISABLED");
    var now = clock.instant();
    registrations.values().forEach(xs -> xs.removeIf(t -> t.isBefore(now.minusSeconds(3600))));
    registrations.entrySet().removeIf(e -> e.getValue().isEmpty());
    var attempts = registrations.computeIfAbsent(request.getRemoteAddr(), k -> new ArrayList<>());
    if (attempts.size() >= 10) throw new Problem(429, "REGISTRATION_THROTTLED");
    attempts.add(now);
    if (registrations.size() > 2000) registrations.remove(registrations.keySet().iterator().next());
    db.lock(Department.class, 1L);
    var username = AdminService.text(BookingPolicy.optional(v.get("username"), 60), 60);
    if (!username.matches("[A-Za-z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_USERNAME");
    var password = BookingPolicy.optional(v.get("password"), 72);
    AdminService.validatePassword(password);
    var dept = db.get(Department.class, BookingPolicy.id(v, "departmentId"));
    var name = AdminService.text(BookingPolicy.optional(v.get("name"), 120), 120);
    var a = new Account();
    a.username = username;
    a.displayName = name;
    a.kind = "CUSTOMER";
    a.roleId =
        db.query(AccessRole.class, "from AccessRole where name='客户 / Customer'").getFirst().id;
    a.departmentId = dept.id;
    a.passwordHash = encoder.encode(password);
    a.enabled = true;
    db.save(a);
    var c = new Customer();
    c.accountId = a.id;
    c.departmentId = dept.id;
    c.name = name;
    c.phone = BookingPolicy.optional(v.get("phone"), 60);
    c.email = BookingPolicy.optional(v.get("email"), 200);
    if (!c.email.isEmpty() && !c.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new Problem(400, "INVALID_EMAIL");
    c.emailConsent = Boolean.TRUE.equals(v.get("emailConsent"));
    db.save(c);
    return Map.of("ok", true);
  }
}
