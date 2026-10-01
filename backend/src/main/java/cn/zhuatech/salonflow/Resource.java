// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.util.*;

/** 单独占用的座位或房间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "resource")
public class Resource {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
