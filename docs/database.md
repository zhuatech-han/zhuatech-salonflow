# 数据库与迁移

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权与定制开发微信：zhuatech / zhuatech2

MySQL8.4、库名zhuatech_salonflow，结构源为 `backend/src/main/resources/db/migration/V1__salon_schema.sql`。JPA `validate`，Flyway负责版本和校验；不是运行时自动建表。

| 业务 | 数据表 |
|---|---|
| 账号/权限 | department、account、access_role、permission、role_permission、nav_menu |
| 参数/字典/审计 | system_setting、dictionary_entry、audit_event |
| 服务/人员/资源 | service_category、salon_service、staff_member、staff_service、resource |
| 客户/时间 | customer、work_shift、blocked_time |
| 原单/流水/记录 | appointment、payment_entry、booking_event、message_job、mutation_stamp |

共22张业务/配置表（含角色与技能关联表），另有flyway_schema_history。金额DECIMAL(16,2)，预约时长/缓冲为整数分钟，UTC时间为timestamp(6)，外键保护引用。每员工每日最多一条排班、一段休息，服务适配与资源同门店。

空库启动只初始化部门1、管理员、五类角色、15种权限、菜单、付款方式和参数。管理员用户名来自ADMIN_USERNAME（默认admin），BCrypt密码来自强度校验后的ADMIN_PASSWORD。其余角色无共享默认账号。可选SEED_DEMO增加DEMO演示服务/座位，不创建假交易。

已有迁移不得改写。新增V2及后续版本，先备份，使用可丢弃副本测试升级与恢复，再在正式部署执行。Flyway校验失败应检查版本来源，禁止设置忽略或repair来隐藏未经核实的结构变化。首次启动、重启与恢复均核对账户和流水。

容量与结账写入READ_COMMITTED + 部门1行锁，在取得锁后读取原单，修订号校验和资金追加在同一事务；幂等指纹记录提交成功结果。客户导入1–500条同事务，任意条错误整批回滚。禁止自行修改流水表当作撤销或退款。
