// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 持久化SMTP通知队列；未配置、取消、无同意及失败均明确记录，不把SMTP接受等同客户阅读。官网 https://www.zhuatech.cn/；微信 zhuatech /
 * zhuatech2。
 */
@Service
public class MailWorker {
  final Store db;
  final Clock clock;
  final BookingService bookings;
  final JavaMailSenderImpl sender = new JavaMailSenderImpl();
  final String host, from;
  final boolean enabled;

  public MailWorker(
      Store db,
      Clock clock,
      BookingService bookings,
      @Value("${salonflow.smtp-host}") String host,
      @Value("${salonflow.smtp-port}") int port,
      @Value("${salonflow.smtp-user}") String user,
      @Value("${salonflow.smtp-password}") String password,
      @Value("${salonflow.smtp-from}") String from,
      @Value("${salonflow.smtp-starttls}") boolean tls,
      @Value("${salonflow.smtp-ssl}") boolean ssl,
      @Value("${salonflow.reminders-enabled}") boolean enabled) {
    this.db = db;
    this.clock = clock;
    this.bookings = bookings;
    this.host = host;
    this.from = from;
    this.enabled = enabled;
    sender.setHost(host);
    sender.setPort(port);
    sender.setUsername(user);
    sender.setPassword(password);
    var p = sender.getJavaMailProperties();
    p.setProperty("mail.smtp.auth", String.valueOf(!user.isBlank()));
    p.setProperty("mail.smtp.starttls.enable", String.valueOf(tls));
    p.setProperty("mail.smtp.starttls.required", String.valueOf(tls));
    p.setProperty("mail.smtp.ssl.enable", String.valueOf(ssl));
    p.setProperty("mail.smtp.connectiontimeout", "5000");
    p.setProperty("mail.smtp.timeout", "5000");
    p.setProperty("mail.smtp.writetimeout", "5000");
  }

  /** 每30秒最多处理20任务，先验证当前预约版本，临时失败五分钟后再试、最多三次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(
      fixedDelayString = "${salonflow.mail-delay:30000}",
      initialDelayString = "${salonflow.mail-delay:30000}")
  @Transactional
  public void dispatch() {
    if (!enabled) return;
    db.lock(Department.class, 1L);
    var now = clock.instant();
    var jobs =
        db
            .query(
                MessageJob.class,
                "from MessageJob where status='QUEUED' and dueAt<=?1 order by id",
                now)
            .stream()
            .limit(20)
            .toList();
    for (var j : jobs) {
      var o = db.get(Appointment.class, j.appointmentId);
      var c = db.get(Customer.class, o.customerId);
      if (j.revision != o.revision
          || j.kind.equals("REMINDER")
              && (!o.status.equals("CONFIRMED") || !o.startsAt.isAfter(now))) {
        j.status = "CANCELLED";
        continue;
      }
      if (!c.enabled
          || c.accountId != null && !db.get(Account.class, c.accountId).enabled
          || !c.emailConsent
          || c.email.isBlank()) {
        j.status = "SKIPPED";
        j.resultCode = "NO_RECIPIENT_CONSENT";
        continue;
      }
      if (host.isBlank() || from.isBlank()) {
        j.status = "NOT_CONFIGURED";
        j.resultCode = "SMTP_NOT_CONFIGURED";
        continue;
      }
      j.attempts++;
      try {
        var m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(c.email);
        m.setSubject(bookings.setting("companyName") + " · " + j.kind + " · " + o.number);
        m.setText(
            o.customerName
                + "\n"
                + o.serviceName
                + "\n"
                + o.startsAt.atZone(bookings.zone())
                + "\n"
                + o.staffName
                + "\n"
                + o.resourceName
                + "\nStatus: "
                + o.status
                + "\nPlease sign in to view or change your appointment. / 请登录预约页面查看或改期。");
        sender.send(m);
        j.status = "SENT";
        j.resultCode = "SMTP_ACCEPTED";
        j.sentAt = now;
      } catch (org.springframework.mail.MailException e) {
        j.resultCode = "SMTP_ERROR";
        j.dueAt = now.plusSeconds(300);
        if (j.attempts >= 3) j.status = "FAILED";
      }
    }
  }

  /** 有权限的人员在修复SMTP配置后重试失败或未配置任务，旧预约版本仍不会发送。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public void retry(Long id, AccessService access) {
    access.require("messages");
    db.lock(Department.class, 1L);
    var j = db.get(MessageJob.class, id);
    var o = db.get(Appointment.class, j.appointmentId);
    access.department(o.departmentId);
    if (!Set.of("FAILED", "NOT_CONFIGURED").contains(j.status))
      throw new Problem(409, "INVALID_STATE");
    j.status = "QUEUED";
    j.attempts = 0;
    j.dueAt = clock.instant();
    j.resultCode = "";
    access.audit("MAIL_RETRY", j.id, o.departmentId);
  }
}
