# 部署与开发

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权与定制开发微信：zhuatech / zhuatech2

## Docker Compose

环境：Docker Engine、Compose v2，建议4GB可用内存；首次构建需要访问镜像仓库、Maven Central和npm。

1. `python3 scripts/init-env.py` 在根目录创建权限600的私有 `.env`，已有文件不覆盖。
2. 设置门店初始管理员用户名、三个不同强密码及本机端口。
3. `docker compose config --quiet` 验证配置。
4. `docker compose up -d --build --wait`，首次启动创建新卷并执行 Flyway V1。
5. 打开 http://127.0.0.1:8100/，以 `.env` 中的管理员登录，按操作手册创建服务、员工和排班。

可为隔离测试使用 `docker compose --project-name salonflow-test --env-file /path/to/private-test.env ...`，避免重用正式数据卷。只清理自己这次创建的测试项目。`down` 保留卷，`down -v` **删除该项目数据**，不能用于保留业务的停止操作。

| 环境变量 | 用途/默认值 |
|---|---|
| MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD | 必填独立密码；不提供弱默认值 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 空数据库管理员初始化；默认用户名admin，密码必填 |
| WEB_PORT / BIND_ADDRESS | 8100 / 127.0.0.1，可避免端口冲突 |
| SEED_DEMO | false；true增加DEMO分类/服务/座位，不伪造经营数据 |
| COOKIE_SECURE | false适用于本机HTTP；HTTPS公开部署设true |
| DATABASE_URL / DATABASE_USER | 可覆盖MySQL连接；默认Compose内部mysql与salonflow用户 |
| SMTP_HOST / SMTP_FROM | 空则队列记录NOT_CONFIGURED，不发送 |
| SMTP_PORT | 587 |
| SMTP_USERNAME / SMTP_PASSWORD | SMTP认证信息，可使用不需认证的隔离测试SMTP |
| SMTP_STARTTLS / SMTP_SSL | true / false；按服务商选择587 STARTTLS或465 SSL，勿同时开启 |
| REMINDERS_ENABLED | true；false停止所有邮件任务调度，不仅提醒邮件 |

环境变量改动需 `docker compose up -d` 重新创建相应容器；业务系统参数在后台修改。现有数据库不会被 ADMIN_PASSWORD 覆盖，重置需使用已授权管理员界面。MySQL默认不暴露主机端口，后端也只在内部网络访问；前端通过Nginx转发 `/api`，不写死浏览器后端地址。

## 本机开发

Java21、Maven3.9、Node24.19.0、Python3，数据库使用MySQL8.4。单独启动数据库时使用Compose或独立测试基础设施，不停其他项目。

后端从进程环境读取 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`、`ADMIN_PASSWORD`，例连接格式 `jdbc:mariadb://127.0.0.1:3306/zhuatech_salonflow?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`。创建空库并仅授予目标数据库权限。应用自行迁移。`mvn -B -f backend/pom.xml spring-boot:run`。

前端在frontend目录执行 `npm ci --no-audit --no-fund` 与 `npm run dev`，Vite将 `/api` 代理到127.0.0.1:8080。开发地址127.0.0.1:5173，仅用于开发。`.env` 不会自动载入本机Java进程；通过本地环境管理工具加载所需变量，不要把真实值粘贴到公开命令或日志。

## HTTPS 与安全

前置可信HTTPS反向代理，只开放必要端口，配置 `COOKIE_SECURE=true`，确认同源Cookie、CSRF和转发头；不要将内部后端独立暴露。公开注册添加网关频率限制，按需要关闭注册。默认每IP每小时最多10次注册尝试、登录8次失败/5分钟、30分钟会话超时；进程重启会重置内存限流，不能代替网关。

默认数据库 `sslMode=trust` 只用于隔离Compose网络。外部MySQL使用 `sslMode=verify-full` 和可信CA，禁止复制该默认值到不可信网络。密钥放外部管理，定期备份、轮换密码、检查服务及容量；原始备份不提交Git。

## 备份与恢复

在自己的部署项目根目录：

```sh
umask 077
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces --set-gtid-purged=OFF zhuatech_salonflow' > salonflow-backup.sql
```

仅在**独立测试数据库**恢复，不覆盖业务数据。启动隔离MySQL且等待健康，将私有备份通过标准输入导入：

```sh
docker compose --project-name salonflow-restore --env-file /path/to/private-restore.env exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" zhuatech_salonflow' < salonflow-backup.sql
```

启动对应后端后核对Flyway、原单、收退款流水、账号权限和可登录状态。先完成恢复演练，再升级正式实例。备份含密码哈希和联系方式，限定访问并加密保存。

## 常见故障

- 登录失败：读自己的 `.env`，不要使用别的项目密码；已有库初始变量不重置账号。
- 无可用时间：核对员工启用、关联账号、服务技能、周排班、休息、房间/座位、请假、时间窗口和已有预约；服务进行中持续占用。
- 写入403：确认角色与范围；浏览器重新登录刷新会话和CSRF。
- 镜像依赖下载失败：查看构建日志，修复网络后安全重试。构建预取和打包均设有限重试，不跳过测试。
- MySQL迁移失败：保留原日志，确认目标为空或迁移版本匹配；禁止删除业务卷或改旧迁移来绕过校验。
- 邮件NOT_CONFIGURED/FAILED：配置SMTP与同意信息，重建后端，通知列表重试；SENT仅代表SMTP接受。
- 时间异常：后台设置IANA时区；已有预约不能切换时区，夏令时跳过、重复的本地时间及跨时钟跳变的服务区间拒绝预约。
