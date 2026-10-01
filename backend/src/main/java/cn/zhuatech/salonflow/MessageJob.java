// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** 持久化预约邮件与提醒；失败状态明确，不伪造送达。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "message_job")
public class MessageJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "appointment_id", nullable = false)
  public Long appointmentId;

  @Column(name = "kind", nullable = false, length = 30)
  public String kind;

  @Column(name = "revision", nullable = false)
  public long revision = 0;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(name = "status", nullable = false, length = 30)
  public String status = "QUEUED";

  @Column(name = "attempts", nullable = false)
  public int attempts = 0;

  @Column(name = "result_code", nullable = false, length = 60)
  public String resultCode = "";

  @Column(name = "sent_at", nullable = true)
  public Instant sentAt;
}
