# API 与授权

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权与定制开发微信：zhuatech / zhuatech2

API基址 `/api`，JSON输入/输出；Session认证，Cookie由浏览器保管。`GET /auth/csrf` 得到header/token，每个写请求附该header。成功一般200，输入400、未登录401、越权403、冲突/过期409、不存在404、容量413。错误正文code，前端中英文解释。业务返回没有推广载荷。

| 方法/路径 | 行为 |
|---|---|
| GET /public/catalog | 匿名服务、门店名、时区、货币、注册开关，无客户或账号 |
| POST /public/register | 强密码、固定客户角色、注册限流；需CSRF |
| POST /auth/login、/auth/logout | 登录/退出；需CSRF |
| GET /auth/me | 实时账号、角色、权限和菜单 |
| POST /auth/password | 本人原密码校验，更新后重新登录 |
| GET /catalog、/dashboard | 受范围限制的业务目录/今日指标 |
| GET /lists/{resource} | 搜索、状态、排序、page/size；每类独立权限 |
| POST/PUT/DELETE /master/{type}[/{id}] | categories/services/staff/resources/customers/shifts/blocks |
| POST /master/customers/import | 1–500个客户JSON对象，整批事务 |
| GET /slots | serviceId/staffId/resourceId/date，可选appointmentId查询本人有权改期的原单时段 |
| POST /appointments | 建预约；客户身份由服务器取得，前台需customerId |
| GET /appointments/{id} | 同一权限范围内原单、流水、事件 |
| POST /appointments/{id}/{action} | reschedule/cancel/arrive/no-show/reassign/start/finish/checkout/pay/refund |
| PUT /my-profile | 客户本人的联系信息和邮件同意 |
| GET /reports、/reports.csv | from/to门店日期，最多366天，需report权限 |
| POST /messages/{id}/retry | FAILED/NOT_CONFIGURED通知重排，需messages权限 |
| POST/PUT/DELETE /admin/{type}[/{id}] | 用户/角色/部门等；需admin及ALL范围 |
| GET /actuator/health（根路径，不加/api） | 匿名健康检查 |

创建预约输入 `serviceId,staffId,resourceId?,customerId?,startsAt,note?,requestKey`；startsAt为UTC ISO整分钟，开始点按门店本地时间每15分钟。改期多传 `revision`，服务ID不能替换，仍用原时长/价格。请求键≤80字符且同操作者、动作、目标、内容完全一致才允许重试。

所有原单动作需 `revision,requestKey`。checkout另传discount、有折扣note；pay传正amount、method（启用付款字典）、reference；refund传原sourceId、正amount、reference、note。未知或非法状态不接受，不用重新生成键掩盖失败。

blocks界面输入 `startsLocal,endsLocal`（门店本地 `YYYY-MM-DDTHH:mm`）；兼容明确UTC的startsAt/endsAt，不接受夏令时歧义。shifts存weekday1–7、startMinute/endMinute0–1440、breakStart/breakEnd；日结束24:00用1440。

客户字段name、departmentId、enabled、email/phone/note/emailConsent；来源是本人注册或有客户管理权限的前台。注册不接受自选kind或角色。任何客户端传入账号ID、门店范围或其他客户ID都不能绕过授权。

审计记录管理员和业务写入，原单事件记录状态变更。API没有任意文件路径、原始SQL或任意实体查询接口；CSV只导出权限范围内业务流水。
