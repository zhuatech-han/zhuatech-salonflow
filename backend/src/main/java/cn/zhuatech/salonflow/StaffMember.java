// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.util.*;

/** 服务人员及可提供项目；关联独立登录账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "staff_member")
public class StaffMember {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "account_id", nullable = false)
  public Long accountId;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "staff_service", joinColumns = @JoinColumn(name = "staff_id"))
  @Column(name = "service_id")
  public Set<Long> services = new HashSet<>();
}
