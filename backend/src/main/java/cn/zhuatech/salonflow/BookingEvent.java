// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** 预约流转与改期事件保留原日期和人员证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "booking_event")
public class BookingEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "appointment_id", nullable = false)
  public Long appointmentId;

  @Column(name = "kind", nullable = false, length = 30)
  public String kind;

  @Column(name = "note", nullable = false, length = 2000)
  public String note;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
