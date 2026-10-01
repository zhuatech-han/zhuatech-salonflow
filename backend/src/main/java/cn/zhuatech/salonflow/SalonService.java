// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

/** 服务目录的价格、时长及资源要求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "salon_service")
public class SalonService {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "category_id", nullable = false)
  public Long categoryId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "price", nullable = false, precision = 16, scale = 2)
  public BigDecimal price = BigDecimal.ZERO.setScale(2);

  @Column(name = "duration_minutes", nullable = false)
  public int durationMinutes = 30;

  @Column(name = "buffer_minutes", nullable = false)
  public int bufferMinutes = 0;

  @Column(name = "resource_required", nullable = false)
  public boolean resourceRequired = false;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
