# StudyWorkplace · 小学生学习督促平台

一个面向家庭的"学习 + 生活"双轨激励系统：金币不可变流水账本、人教版三科（1–6 年级）教材打卡与知识点自测、掌握度知识树、生活营（作息/家务/屏幕时间/习惯链）、家长 PIN 后台与全量备份导入导出。

本项目按 **SSD（Spec-Driven Development）** 范式开发：`docs/` 下的规格文档是唯一事实来源，代码与文档冲突时先改文档再改代码。功能与验收状态见 [docs/feature_list.json](docs/feature_list.json)，任务拆解与验收标准见 [docs/06-任务拆解与验收.md](docs/06-任务拆解与验收.md)。

## 技术栈

- 后端：Spring Boot 3.2（Java 17+）+ Spring Data JPA + Jackson
- 数据库：MySQL 8（自动建库 `studyworkplace`，表结构以 [docs/05-数据模型规格.md](docs/05-数据模型规格.md) 与 `schema.sql` 为唯一事实，`ddl-auto: validate`）
- 前端：原生 HTML/CSS/JS（ESM 模块、hash 路由），由 Spring Boot 直接伺服，**无 Node / Python 构建链**

## 从零跑起来

前置：JDK 17+、Maven、MySQL 8 在本机 3306 运行。

1. 配置数据库账号：编辑 `src/main/resources/application.yml` 中 `spring.datasource.username/password`（默认 root 密码已写入该文件，仅适合家庭本机）。
2. 启动：
   - **推荐（Windows 双击即用）**：`scripts\start.bat` 后台隐藏启动，成功后打印信息面板（本机 + 手机/平板局域网地址、运行/错误日志与 PID 文件路径），结束前**按任意键确认再退出**（双击运行不会闪退）；`scripts\stop.bat` 停止（先校验目标确为 java 进程再结束，防误杀）；`scripts\restart.bat` 先停后起、只在最后确认一次。可选参数：`build`（如 `scripts\restart.bat build`）先重新打包再启动；`auto` 跳过按键确认（供脚本/自动化调用）。日志在 `logs\app.out.log` / `app.err.log`，进程号在 `run\app.pid`。脚本按 GBK 编码保存以兼容中文控制台，修改后须重新转 GBK+CRLF，请勿直接提交 UTF-8 版本。
   - 开发模式：
     ```bash
     mvn spring-boot:run
     ```
     或先打包再运行：
     ```bash
     mvn package -DskipTests
     java -jar target/studyworkplace-1.0.0.jar
     ```
3. 浏览器打开 `http://127.0.0.1:8080/`。首次运行会自动建库建表并装载三科教材/知识/题库种子（语文 72 叶、数学 150 叶含奥数、英语 72 叶）。
4. 页面引导创建第一个孩子档案，即可使用。**家长后台 PIN 默认 `8888`**，进入 `#/admin` 后请尽快修改。

测试：`mvn test`（37 个用例，覆盖账本/去重/掌握度引擎/生活规则/备份校验和/PIN 哈希/审批状态机等领域逻辑，以及「4–6 年级每知识点恰 15 题」的种子契约断言）。

## 手机 / 平板访问（v2.4 H5）

- 默认 `server.address: 0.0.0.0`，监听全部网卡；与电脑在同一 Wi-Fi 的手机/平板浏览器直接访问 `http://<主机局域网IP>:8080/` 即可，具体地址在应用顶栏提示和后台「数据」页（lanHint）都能看到。
- 全站响应式：≤480px 汉堡抽屉导航、表格竖排成卡片、触控目标 ≥44px、弹窗底部抽屉化，孩子端与家长后台（含上传照片/电子书）都可在手机上完成。
- Windows 首次启动如弹出防火墙提示请允许，或手动放行 8080 端口；仅本机使用时可把 `server.address` 改回 `127.0.0.1`。

注意：应用没有账号体系，局域网内所有设备共享同一份数据，请只在可信家庭网络开放；后台配置全部在家长 PIN 之后。

## 任务佐证照片（v2.4）

孩子（或家长代记）提交需要审批的加/扣分时，可拍照或从相册选 ≤3 张照片并写备注：服务端自动压长边 ≤1600、打 4 行水印（含**上传时刻**、事项、备注）存入 `data/photos/`。家长在「审批中心」点行内 📷 角标即可看大图核验，通过后才落账；驳回则回到未勾状态。仅支持 JPEG/PNG/WebP、单张 ≤8MB；照片本体不进数据库、不参与备份（备份只存元数据，schemaVersion 3.1），换机时请连同 `data/photos/` 一起拷贝。

## 备份与迁移

- 顶栏「⬇️ 导出备份」下载全量 JSON（含 SHA-256 校验和、schemaVersion 3.1；待审动作/扣分预设/审批配置/照片元数据均入段）。
- 「⬆️ 导入备份」：选择文件 → 校验 + 摘要预览（档案数/流水数/差异警告）→ 确认覆盖；导入前当前数据已快照留存；老版本（2.0/3.0）缺新段容忍导入、校验和不失配。
- 数据库层备份：直接 `mysqldump studyworkplace` 亦可，换机时整库迁移即可恢复。

## 人教版电子书（M4.7 / v2.4 上传通道）

两种方式，选其一即可：

1. **后台上传（推荐，手机也能操作）**：家长 PIN 进入 `#/admin` → 「📚 教材资源」，36 格（3 科 × 6 年级 × 2 学期）网格按册上传或替换 PDF（≤80MB，同名覆盖），上传后孩子端「📖 打开电子书」**立即可读**，无需重启。
2. **直接放文件**：在项目根目录 `data/ebooks/` 放入 PDF，按 `<学科>-<年级><学期>.pdf` 命名，例如 `math-4上.pdf`、`cn-4上.pdf`、`en-4下.pdf`（学科：cn 语文 / math 数学 / en 英语；学期：上 / 下）。

未上传的册次，孩子端打开时会显示引导页（告诉孩子去找家长上传），不会出现 404 空白。电子书文件不进数据库、不参与备份导出；换机时单独拷贝 `data/ebooks/` 即可。若想从某单元对应课本页码打开，可在后台导入的教材目录 JSON 分组上可选加 `"ebookPage": 12` 字段（docs/10 契约）。

获取正版电子课本：人教版（人民教育出版社）官方渠道为「人教社官网 pep.com.cn」与国家中小学智慧教育平台 basic.smartedu.cn（免费，含小学全科电子教材），下载对应册次 PDF 后按上述方式上传即可。仓库自带的 `data/ebooks/*.pdf` 为验收占位样例，请替换为正式教材内容。

## 目录结构（参考结构，非规范约束）

```
src/main/java/com/studdy/
  domain/     # 纯业务规则（LedgerRule/MasteryEngine/ScreenRule/BackupFormat…），零 Spring/JPA 依赖
  entity/ repo/   # JPA 实体与仓储
  service/    # 事务编排（LedgerService.appendTx 是金币唯一写入口，M9.1）
  web/        # REST 控制器 + 家长门
src/main/resources/
  application.yml schema.sql seed/   # 配置、建表、三科种子内容
  static/     # 前端：index.html + css/app.css + js/(api|store|ui|app).js + js/views/{desk,textbook,knowledge,life,shop,admin}.js
tools/        # QuizExpander.java：零依赖 JDK 工具，把 4–6 年级每叶自测题补齐到 15 题（幂等，答案程序计算）
scripts/      # start.bat / stop.bat / restart.bat：Windows 一键运维（GBK 编码，双击即用）
data/         # ebooks/（电子书 PDF）与 photos/（佐证照片成品图），均不进库
docs/         # SSD 规格文档（00–10、99）+ feature_list.json 状态事实来源
```

## 核心规则速览

- 余额 = Σ流水，永不直接改数；取消勾选生成成对冲正流水（M1）。
- 审批开启时（v2.3，默认开）：所有人为加/扣分先入「待审」队列——半透明勾呈现，不计余额/报表/连续天数；家长审批通过才写账本。待审金额存绝对值快照，扣分（PENALTY）落账时取反，界面按类型显示「+4🪙 / −5🪙」。屏幕超时扣币与商店兑换保持即时（M11.5）。
- 家长「数据修正」= 冲正原流水 + 补录新手动流水成对记账，原流水永不改写（M7.6）。
- 每日一次去重由数据库唯一约束兜底（M9.2/M2）。
- 掌握度六档：精通/掌握/部分/未掌握/未答/待重测，颜色 + 形状双通道呈现（M5.5，色盲友好）。
- 孩子端可触发动作仅限：计划勾选、教材打卡、自测、兑换、生活打卡、屏幕计时、切档案；其余全在家长门后（M9.5）。
- 危险区（重置档案/删档案/清空）需输入档案名确认词 + 二次确认（M7.4）。
