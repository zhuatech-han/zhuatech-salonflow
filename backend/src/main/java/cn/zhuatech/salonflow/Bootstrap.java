// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次空库创建管理和岗位，不生成伪造预约或收入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;
  final boolean demo;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${salonflow.admin-username}") String username,
      @Value("${salonflow.admin-password}") String password,
      @Value("${salonflow.seed-demo}") boolean demo) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
    this.demo = demo;
  }

  /** 用独立强密码初始化；重启不覆盖账号或经营数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_USERNAME");
    var d = new Department();
    d.name = "主门店 / Main salon";
    db.save(d);
    String[][] ps = {
      {"dashboard", "工作台 / Overview"},
      {"master.read", "查看经营档案 / View masters"},
      {"master.write", "维护经营档案 / Edit masters"},
      {"customer.write", "客户档案 / Client records"},
      {"schedule", "排班与请假 / Scheduling"},
      {"appointment.read", "查看预约 / View appointments"},
      {"appointment.manage", "预约接待 / Reception"},
      {"execute", "本人服务 / Own services"},
      {"checkout", "结账收款 / Checkout"},
      {"refund", "原单退款 / Refunds"},
      {"customer.book", "客户自助预约 / Client booking"},
      {"report", "经营统计 / Reports"},
      {"messages", "邮件通知任务 / Notifications"},
      {"audit", "操作审计 / Audit"},
      {"admin", "系统管理 / Administration"}
    };
    Set<String> all = new HashSet<>();
    for (var v : ps) {
      var p = new Permission();
      p.code = v[0];
      p.name = v[1];
      db.save(p);
      all.add(v[0]);
    }
    var ar = role("管理员 / Administrator", all, "ALL");
    role(
        "店长 / Manager",
        Set.of(
            "dashboard",
            "master.read",
            "master.write",
            "customer.write",
            "schedule",
            "appointment.read",
            "appointment.manage",
            "checkout",
            "refund",
            "report",
            "messages"),
        "DEPARTMENT");
    role(
        "前台 / Reception",
        Set.of(
            "dashboard",
            "master.read",
            "customer.write",
            "appointment.read",
            "appointment.manage",
            "checkout"),
        "DEPARTMENT");
    role(
        "服务员工 / Stylist",
        Set.of("dashboard", "master.read", "appointment.read", "execute"),
        "ASSIGNED");
    role("客户 / Customer", Set.of("dashboard", "appointment.read", "customer.book"), "ASSIGNED");
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.roleId = ar.id;
    a.departmentId = d.id;
    a.passwordHash = encoder.encode(password);
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "今日门店", "Today", "dashboard"},
      {"appointments", "预约日历", "Appointments", "appointment.read"},
      {"book", "在线预约", "Book online", "customer.book"},
      {"services", "服务项目", "Services", "master.read"},
      {"categories", "项目分类", "Categories", "master.read"},
      {"staff", "员工与技能", "Team & skills", "master.read"},
      {"resources", "座位与房间", "Seats & rooms", "master.read"},
      {"shifts", "每周排班", "Weekly shifts", "schedule"},
      {"blocks", "请假与停用时段", "Blocked time", "schedule"},
      {"customers", "客户档案", "Clients", "customer.write"},
      {"payments", "收退款流水", "Payment ledger", "checkout"},
      {"reports", "经营统计", "Reports", "report"},
      {"messages", "邮件通知", "Notifications", "messages"},
      {"users", "登录账号", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles & permissions", "admin"},
      {"departments", "门店部门", "Departments", "admin"},
      {"menus", "菜单管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
      {"settings", "系统参数", "Settings", "admin"},
      {"audit", "操作审计", "Audit", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of(
                "companyName",
                "知华美业 / ZhuaTech SalonFlow",
                "currency",
                "CNY",
                "timezone",
                "Asia/Shanghai",
                "bookingLeadMinutes",
                "15",
                "cancelLeadMinutes",
                "60",
                "publicRegistration",
                "true")
            .entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    for (var v :
        new String[][] {
          {"CASH", "现金", "Cash"},
          {"BANK", "银行转账", "Bank transfer"},
          {"EXTERNAL", "外部收款凭据", "External payment record"}
        }) {
      var x = new DictionaryEntry();
      x.type = "payment_method";
      x.code = v[0];
      x.name = v[1];
      x.nameEn = v[2];
      db.save(x);
    }
    if (demo) {
      var c = new ServiceCategory();
      c.name = "示例护理 / Demo care";
      c.departmentId = d.id;
      db.save(c);
      var s = new SalonService();
      s.code = "DEMO-CUT";
      s.name = "示例剪发 / Demo haircut";
      s.categoryId = c.id;
      s.departmentId = d.id;
      s.price = new BigDecimal("68.00");
      s.durationMinutes = 45;
      s.bufferMinutes = 15;
      db.save(s);
      var r = new Resource();
      r.code = "DEMO-SEAT";
      r.name = "示例座位 / Demo seat";
      r.departmentId = d.id;
      db.save(r);
    }
  }

  private AccessRole role(String name, Set<String> ps, String scope) {
    var r = new AccessRole();
    r.name = name;
    r.permissions = new HashSet<>(ps);
    r.scope = scope;
    return db.save(r);
  }
}
