// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.salonflow;

import jakarta.persistence.*;
import java.util.*;

/** 员工一周工作和休息区间；同日一条。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "work_shift")
public class WorkShift {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "staff_id", nullable = false)
  public Long staffId;

  @Column(name = "weekday", nullable = false)
  public int weekday = 1;

  @Column(name = "start_minute", nullable = false)
  public int startMinute = 540;

  @Column(name = "end_minute", nullable = false)
  public int endMinute = 1080;

  @Column(name = "break_start", nullable = false)
  public int breakStart = 0;

  @Column(name = "break_end", nullable = false)
  public int breakEnd = 0;
}
