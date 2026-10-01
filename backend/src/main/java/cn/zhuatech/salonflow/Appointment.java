// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** 预约和结账原单快照；状态、乐观修订与累计款项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "appointment")
public class Appointment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "number", nullable = false, length = 60)
  public String number;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "staff_id", nullable = false)
  public Long staffId;

  @Column(name = "service_id", nullable = false)
  public Long serviceId;

  @Column(name = "resource_id", nullable = true)
  public Long resourceId;

  @Column(name = "customer_name", nullable = false, length = 120)
  public String customerName;

  @Column(name = "staff_name", nullable = false, length = 120)
  public String staffName;

  @Column(name = "service_name", nullable = false, length = 120)
  public String serviceName;

  @Column(name = "resource_name", nullable = false, length = 120)
  public String resourceName = "";

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "blocked_until", nullable = false)
  public Instant blockedUntil;

  @Column(name = "duration_minutes", nullable = false)
  public int durationMinutes = 0;

  @Column(name = "buffer_minutes", nullable = false)
  public int bufferMinutes = 0;

  @Column(name = "price", nullable = false, precision = 16, scale = 2)
  public BigDecimal price = BigDecimal.ZERO.setScale(2);

  @Column(name = "discount", nullable = false, precision = 16, scale = 2)
  public BigDecimal discount = BigDecimal.ZERO.setScale(2);

  @Column(name = "total", nullable = false, precision = 16, scale = 2)
  public BigDecimal total = BigDecimal.ZERO.setScale(2);

  @Column(name = "paid", nullable = false, precision = 16, scale = 2)
  public BigDecimal paid = BigDecimal.ZERO.setScale(2);

  @Column(name = "refunded", nullable = false, precision = 16, scale = 2)
  public BigDecimal refunded = BigDecimal.ZERO.setScale(2);

  @Column(name = "status", nullable = false, length = 30)
  public String status = "CONFIRMED";

  @Column(name = "revision", nullable = false)
  public long revision = 0;

  @Column(name = "note", nullable = false, length = 1000)
  public String note = "";

  @Column(name = "service_note", nullable = false, length = 1000)
  public String serviceNote = "";

  @Column(name = "checked_out", nullable = false)
  public boolean checkedOut = false;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @Column(name = "service_started_at", nullable = true)
  public Instant serviceStartedAt;

  @Column(name = "service_finished_at", nullable = true)
  public Instant serviceFinishedAt;

  @Column(name = "completed_at", nullable = true)
  public Instant completedAt;
}
