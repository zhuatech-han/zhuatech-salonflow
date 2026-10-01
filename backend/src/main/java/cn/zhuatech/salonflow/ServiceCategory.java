// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.util.*;

/** 项目分类与引用保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "service_category")
public class ServiceCategory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
