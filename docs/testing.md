# 验收方法

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权与定制开发微信：zhuatech / zhuatech2

格式：后端Spotless、前端Prettier；静态检查：ESLint与git diff --check。后端JUnit包含独立业务规则测试及真实HTTP/数据库集成测试；H2为MySQL兼容测试模式。前端Node测试时区、夏令时和显示精度，不以静态快照代替核心业务。

后端 `mvn -B -f backend/pom.xml spotless:check test package`，前端在frontend执行 `npm ci --no-audit --no-fund && npm run format:check && npm run lint && npm test && npm run build`。`docker compose config --quiet` 与 `docker compose build`，构建中正常执行测试，Maven缓存带锁、依赖预取及有限网络重试。

MySQL验收必须使用独立项目名及新卷，等待三个服务健康，访问页面和健康接口，管理员登录；创建服务/员工/排班/资源/客户，然后预约→到店→员工开始→完成→核价→分笔收款→原单退款。核对收退款和净额、重复键、越权及匿名拒绝。脚本只在显式 `--allow-test-writes` 才写入TEST虚构数据，不能指向业务数据库。

后端测试重点：员工/资源/客户撞期，缓冲边界，超时进行中服务，价格时长快照与改期时段，非法状态，修订号和并发收款，超收/超退，按原付款退款，零价无假流水，原子导入，固定客户角色，其他客户/员工/门店隔离，账号停用，时区锁定，SMTP接受/未配置/无同意/旧修订取消/三次重试。SMTP成功测试只连接进程内本机接收器，不发送到真实邮箱。

部署后还需验证重启保留原单与登录、Flyway版本和校验、私有备份恢复到独立新卷、旧迁移被改动时启动拒绝。浏览器实际走客户及员工流程，检查中文/英文桌面及手机，截图必须来自当前代码和TEST数据。

发布检查 `python3 scripts/release-check.py` 核对README图片、原二维码SHA256、品牌、LICENSE、示例密码及常见密钥格式；仍需人工审查Git暂存差异和身份/日志内容。扫描不等于已完成渗透测试。

实际本次验收结果见版本发布说明；任何未执行的检查不得称通过。仅删除本次测试资源，保留其他应用及卷。
