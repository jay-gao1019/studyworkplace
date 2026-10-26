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
2. 启动（开发）：
   ```bash
   mvn spring-boot:run
   ```
   或先打包再运行（交付）：
   ```bash
   mvn package -DskipTests
   java -jar target/studyworkplace-1.0.0.jar
   ```
3. 浏览器打开 `http://127.0.0.1:8080/`。首次运行会自动建库建表并装载三科教材/知识/题库种子（语文 72 叶、数学 150 叶含奥数、英语 72 叶）。
4. 页面引导创建第一个孩子档案，即可使用。**家长后台 PIN 默认 `8888`**，进入 `#/admin` 后请尽快修改。

测试：`mvn test`（6 个测试类 23 个用例，覆盖账本/去重/掌握度引擎/生活规则/备份校验和/PIN 哈希等领域逻辑）。

## 局域网给全家设备用

默认 `server.address: 127.0.0.1` 只监听本机。要在平板/其他电脑访问：

1. 把 `application.yml` 的 `server.address` 改为 `0.0.0.0` 后重启；
2. Windows 防火墙放行 8080 端口（或首次启动时允许弹窗）；
3. 同网段设备访问 `http://<主机局域网IP>:8080/`。

注意：应用没有账号体系，局域网内所有设备共享同一份数据，请只在可信家庭网络开放；后台配置全部在家长 PIN 之后。

## 备份与迁移

- 顶栏「⬇️ 导出备份」下载全量 JSON（含 SHA-256 校验和、schemaVersion 2.0）。
- 「⬆️ 导入备份」：选择文件 → 校验 + 摘要预览（档案数/流水数/差异警告）→ 确认覆盖；导入前当前数据已快照留存。
- 数据库层备份：直接 `mysqldump studyworkplace` 亦可，换机时整库迁移即可恢复。

## 人教版电子书（M4.7）

- 在项目根目录 `data/ebooks/` 放入 PDF，按 `<学科>-<年级><学期>.pdf` 命名，例如 `math-4上.pdf`、`cn-4上.pdf`、`en-4下.pdf`（学科：cn 语文 / math 数学 / en 英语；学期：上 / 下）。
- 放好后教材页对应年级的单元标题行会出现「📖 打开电子书」；未放文件时按钮置灰并提示摆放目录。无需重启应用。
- 电子书文件不进数据库、不参与备份导出；换机时单独拷贝 `data/ebooks/` 即可。
- 若想从某单元对应课本页码打开，可在后台导入的教材目录 JSON 分组上可选加 `"ebookPage": 12` 字段（docs/10 契约）。

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
docs/         # SSD 规格文档（00–10、99）+ feature_list.json 状态事实来源
```

## 核心规则速览

- 余额 = Σ流水，永不直接改数；取消勾选生成成对冲正流水（M1）。
- 家长「数据修正」= 冲正原流水 + 补录新手动流水成对记账，原流水永不改写（M7.6）。
- 每日一次去重由数据库唯一约束兜底（M9.2/M2）。
- 掌握度六档：精通/掌握/部分/未掌握/未答/待重测，颜色 + 形状双通道呈现（M5.5，色盲友好）。
- 孩子端可触发动作仅限：计划勾选、教材打卡、自测、兑换、生活打卡、屏幕计时、切档案；其余全在家长门后（M9.5）。
- 危险区（重置档案/删档案/清空）需输入档案名确认词 + 二次确认（M7.4）。
