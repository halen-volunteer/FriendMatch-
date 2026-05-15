# FriendMatch 高校兴趣社群聊天系统

基于 Spring Boot 4 + Vue 3 + WebSocket 构建的高校兴趣社群聊天系统。项目围绕“兴趣匹配 + 社群协作 + 实时聊天 + 平台治理”展开，覆盖用户认证、好友关系、团队协作、实时聊天、举报申诉、处罚通知、搜索推荐、文件上传等完整业务链路，适合作为个人学习项目、课程设计与毕业设计展示项目。

## 项目简介

FriendMatch 是一个前后端分离的社交聊天项目，目标场景是高校兴趣社群与轻量社交。系统既包含用户侧常见功能，也包含平台治理、权限控制、缓存设计、异步通知、WebSocket 实时通信等偏后端工程化能力。

当前仓库包含：

- Spring Boot 后端服务
- Vue 3 前端项目
- 数据库初始化脚本
- 项目补充文档与说明材料

## 核心亮点

- 实时聊天链路：基于 WebSocket 实现单聊、群聊、系统通知推送，支持在线状态维护、未读数统计、离线场景下的消息补达与会话懒加载。
- 消息全生命周期管理：支持消息撤回、编辑、收藏、置顶、会话内搜索、已读回执、群公告等能力。
- 团队协作与角色权限：支持建队、入队申请与审批、成员管理、角色分级、队长转让、全员禁言与成员禁言，并通过 AOP + 自定义注解统一收口权限校验。
- 平台治理闭环：支持用户举报、团队举报、消息举报、管理员审核、处罚执行、申诉处理、反馈回复与系统通知。
- 异步化能力：RabbitMQ 已实际接入登录日志、系统通知、部分业务副作用与异步解耦链路，不是预留依赖。
- 搜索推荐与缓存优化：支持用户搜索、团队搜索、热门搜索、搜索历史与轻量推荐，并结合 Redis 缓存优化高频读场景。
- 文件上传体验：对接七牛云对象存储，支持前端直传、分片上传、断点续传与上传进度反馈。
- 稳定性优化：包含登录态缓存、推荐刷新定时任务、在线状态心跳、WebSocket 重连、历史消息分页读取、大群平滑推送等设计。

## 技术栈

### 后端

- Java 21
- Spring Boot 4.0.3
- MyBatis-Plus 3.5.16
- MySQL 8
- Redis 6+
- RabbitMQ
- WebSocket
- Spring Mail
- Spring AOP / AspectJ
- Hutool
- Fastjson2
- 敏感词检测库 `sensitive-word`
- 七牛云 Kodo SDK

### 前端

- Vue 3
- Vite 7
- Vue Router
- Pinia
- Axios
- Element Plus
- 原生 WebSocket

## 功能模块

### 1. 用户认证与账号管理

- 用户注册、登录、忘记密码
- 图形验证码、邮箱验证码、Token 鉴权
- 用户资料维护、头像上传、兴趣标签设置
- 在线状态维护、登录日志、设备管理
- 单账号唯一登录限制

### 2. 好友关系与隐私控制

- 好友申请、同意、拒绝、删除
- 黑名单管理
- 用户资料可见范围控制
- 邮箱搜索、私聊能力、关系隔离校验

### 3. 团队管理

- 团队创建、编辑、解散
- 入队申请、审批、邀请、退队
- 成员列表、角色调整、队长转让
- 全员禁言、成员禁言、团队公告

### 4. 实时聊天系统

- 单聊、群聊消息实时收发
- 会话列表、未读数、最近消息摘要
- 历史消息分页加载
- 消息撤回、编辑、收藏、置顶、搜索
- 已读回执、系统通知推送

### 5. 举报、处罚、申诉、反馈

- 用户举报、团队举报、消息举报
- AI 审核 + 人工审核协同
- 处罚执行、撤销、处罚记录查询
- 用户申诉、管理员处理
- 反馈提交与回复通知

### 6. 搜索与推荐

- 用户搜索、团队搜索
- 搜索历史、热门关键词
- 推荐用户、推荐团队
- 基于标签、关系、共同团队、聊天关系、活跃度的轻量推荐

### 7. 文件上传

- 七牛云上传凭证下发
- 前端直传图片与文件
- 分片上传、断点续传、进度反馈

## 项目结构

```text
FriendMatch/
├── src/                                # Spring Boot 后端源码
│   ├── main/
│   │   ├── java/com/zero/usercenter/
│   │   │   ├── aop/                    # AOP 切面与权限注解
│   │   │   ├── config/                 # 配置类、定时任务、MQ 配置
│   │   │   ├── Controller/             # 控制层
│   │   │   ├── DTO/                    # 数据传输对象
│   │   │   ├── Enums/                  # 枚举定义
│   │   │   ├── Mapper/                 # MyBatis-Plus Mapper
│   │   │   ├── Model/                  # 实体类
│   │   │   ├── Service/                # 业务层接口与实现
│   │   │   ├── mq/                     # MQ 生产者、消费者、消息体
│   │   │   ├── utils/                  # 工具类、拦截器、上下文
│   │   │   ├── websocket/              # WebSocket 配置与处理器
│   │   │   └── UserCenterApplication.java
│   │   └── resources/
│   │       ├── Mapper/                 # MyBatis XML
│   │       └── application.example.yaml
│   └── test/
├── VueFormatCode/                      # Vue 3 前端项目
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── vite.config.js
├── SQL/                                # 数据库初始化脚本
├── MD/                                 # 项目补充文档
├── pom.xml                             # 后端 Maven 配置
├── mvnw / mvnw.cmd                     # Maven Wrapper
└── README.md
```

## 运行环境

### 后端环境

- JDK 21
- MySQL 8+
- Redis 6+
- RabbitMQ 3.x
- Maven 3.9+，或直接使用项目自带的 `mvnw / mvnw.cmd`

### 前端环境

- Node.js `^20.19.0 || >=22.12.0`
- npm

## 本地启动

### 1. 初始化数据库

执行数据库脚本：

- `SQL/friendmatch.sql`

默认数据库名示例为：

- `friendmatch`

### 2. 配置后端

仓库中不包含真实敏感配置，使用示例配置文件自行复制：

```bash
src/main/resources/application.example.yaml
```

复制为：

```bash
src/main/resources/application.yaml
```

然后按本地环境补充以下配置：

- MySQL
- Redis
- RabbitMQ
- 邮箱 SMTP
- 七牛云 OSS
- AI 审核接口

### 3. 启动后端

方式一：使用 IDE 直接运行

- 启动类：`com.zero.usercenter.UserCenterApplication`

方式二：使用 Maven Wrapper

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

默认端口：

- 后端：`http://localhost:8081`

### 4. 配置前端

前端通过 Vite 环境变量读取接口地址，核心变量有：

- `VITE_API_BASE_URL`
- `VITE_WS_BASE_URL`

开发环境可参考当前本地配置方式：

```bash
VueFormatCode/.env.development
```

示例：

```env
VITE_API_BASE_URL=http://localhost:8081
VITE_WS_BASE_URL=ws://localhost:8081
```

### 5. 启动前端

进入前端目录：

```bash
cd VueFormatCode
```

安装依赖：

```bash
npm install
```

启动开发环境：

```bash
npm run dev
```

默认端口通常为：

- 前端：`http://localhost:5173`
- 
## License

MIT
