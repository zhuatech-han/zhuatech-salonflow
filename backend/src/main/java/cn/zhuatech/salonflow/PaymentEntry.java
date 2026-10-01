// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** 收款和引用原收款的退款不可覆盖流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "payment_entry")
public class PaymentEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "appointment_id", nullable = false)
  public Long appointmentId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "source_id", nullable = true)
  public Long sourceId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "amount", nullable = false, precision = 16, scale = 2)
  public BigDecimal amount;

  @Column(name = "method", nullable = false, length = 60)
  public String method;

  @Column(name = "reference", nullable = false, length = 200)
  public String reference;

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;
}
