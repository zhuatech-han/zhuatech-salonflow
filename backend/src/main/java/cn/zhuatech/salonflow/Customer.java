// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.util.*;

/** 客户联系资料；自助账号与线下客户明确区分。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "customer")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "account_id", nullable = true)
  public Long accountId;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "email", nullable = false, length = 200)
  public String email = "";

  @Column(name = "phone", nullable = false, length = 60)
  public String phone = "";

  @Column(name = "note", nullable = false, length = 1000)
  public String note = "";

  @Column(name = "email_consent", nullable = false)
  public boolean emailConsent = false;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
