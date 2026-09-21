# WMS 智能仓储管理系统

个人学习 / 求职面试用的后端项目。Java 17 · Spring Boot 4 · MyBatis-Plus · MySQL 8（Redis、MQ 后期加）。

## 做什么

跑通仓储完整闭环：入库 → 上架 → 库存 → 出库分配 → 拣货 → 发货。

**当前进度：出入库全链路 + 库存可视化看板 + 库存作业（冻结 / 移库 / 盘点 / 对账）
+ 权限 / 超时释放 / 幂等 / 审计日志。**
112 个测试全过，前后端已联调。
库存作业支持即时动作和单据流（盘点单 / 移库单），保留完整来源流水与批量过账事务。
在跑通的基础上逐步加并发控制、幂等、缓存、消息队列，最后接 AI Agent。

## 不做什么

- 硬件对接（AGV / 输送线）、设备控制层
- 多租户、复杂权限、3D 可视化

> WMS = Warehouse Management System，管业务与库存。
> WCS = Warehouse Control System，管设备与执行，本项目不涉及。

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言 / 框架 | Java 17、Spring Boot 4.1 |
| ORM | MyBatis-Plus 3.5.x |
| 数据库 | MySQL 8，表结构用 Flyway 管理 |
| 缓存 | Redis（第 8 周起） |
| 消息队列 | RabbitMQ 或 RocketMQ（第 8 周起） |
| 接口文档 | SpringDoc + Knife4j |

## 文档

| 文件 | 内容 |
|---|---|
| [docs/01-需求与设计.md](docs/01-需求与设计.md) | 角色、功能清单、业务规则、领域模型、表设计、状态机 |
| [docs/02-开发规范与计划.md](docs/02-开发规范与计划.md) | 工程结构、分层、接口规范、错误码、周计划 |
| [docs/03-入库模块设计与实现.md](docs/03-入库模块设计与实现.md) | 入库链路的关键设计：库存唯一写入口、并发控制、状态机 CAS、单号发号器、踩坑记录 |
| [docs/04-出库模块设计与实现.md](docs/04-出库模块设计与实现.md) | 出库链路的关键设计：三步库存模型、FIFO 分配、乐观读+悲观写、整单原子 |
| [docs/05-库存作业设计与实现.md](docs/05-库存作业设计与实现.md) | 冻结 / 移库 / 盘点 / 对账：四类库存作业的语义、移库的原子性、对账为什么不写流水 |
| [docs/06-权限·超时释放·幂等与审计.md](docs/06-权限·超时释放·幂等与审计.md) | 认证与授权怎么分开、超时释放（单据级 BR-010 + 任务级）、幂等表怎么用（含两个踩坑）、审计切面 |
| [sql/V1.0.0__init_schema.sql](sql/V1.0.0__init_schema.sql) | 建表脚本（Flyway 用，不含 DROP） |

## 怎么跑

1. 建库
   ```sql
   CREATE DATABASE wms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```
2. 自己建 Spring Boot 4 工程，把 `sql/V1.0.0__init_schema.sql` 放到 `src/main/resources/db/migration/`
3. pom 加 Flyway 依赖
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-flyway</artifactId>
   </dependency>
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-mysql</artifactId>
   </dependency>
   ```
4. `application.yml` 配数据源 + `spring.flyway.locations: classpath:db/migration`，启动应用自动建表
5. 默认账号 `admin` / `123456`

**角色与演示账号**（用来体验权限差异）：

| 账号 | 密码 | 角色 | 能干什么 |
|---|---|---|---|
| `admin` | `123456` | 系统管理员 | 一切 + 用户管理 |
| `mgr001` | `mgr001pass` | 仓库主管 | 主数据 + 库存调整（冻结/移库/盘点/对账） |
| `op001` | `op001pass` | 作业员 | 收货 / 上架 / 拣货 + 查询 |

> Spring Boot 4 把 Flyway 自动配置拆成了独立模块，**只加 `flyway-core` 不生效**，必须用上面的 starter。

## 进度

| 周次 | 主题 |
|---|---|
| 1 | 需求与设计 |
| 2 | 工程骨架 + 基础数据 CRUD |
| 3 | 入库单、收货登记、库存内核与流水 |
| 4 | 上架任务状态机、库位推荐、上架入库 |
| 5-6 | 出库单、FIFO 库存分配、拣货任务、发货核销 |
| 7 | 冻结、移库、盘点、对账（读接口 + 业务动作写入，全部走库存唯一写入口） |
| 7 | 权限（三角色 + 用户管理）、BR-010 超时释放、幂等（含处理中租约）、审计日志 |
| — | 库存可视化：工作台看板、三维度汇总、库存预警、库位占用率 |
| 8 | Redis 与 MQ |
| 9 | 压测与部署 |
| 10+ | AI Agent |
