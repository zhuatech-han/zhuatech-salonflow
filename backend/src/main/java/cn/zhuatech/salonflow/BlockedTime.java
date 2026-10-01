// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** 员工请假或资源暂停的实际UTC区间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "blocked_time")
public class BlockedTime {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "staff_id", nullable = true)
  public Long staffId;

  @Column(name = "resource_id", nullable = true)
  public Long resourceId;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason;
}
