# FriendMatch 综合实现文档 - Part 1：用户认证模块

> 版本：V2.2（全接口版，Part1模板） | 日期：2026-04-03

---

## 一、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `LoginController.java` |
| Service | `UserService.java` / `UserServiceImpl.java` |
| Mapper | `UserMapper.java` / `UserLoginLogMapper.java` |
| Model | `User.java` / `UserLoginLog.java` |
| DTO | `LoginDTO.java` |

---

## 二、数据库设计

### `t_user`（认证相关字段）

| 字段 | 说明 |
|---|---|
| `user_account` | 唯一公开账号 |
| `user_email` | 邮箱登录标识 |
| `user_password` | BCrypt 密文 |
| `global_punish_type` | 0无/1禁言/2封号 |
| `global_unpunish_time` | 处罚解除时间 |

### `t_user_login_log`

| 字段 | 说明 |
|---|---|
| `user_id` | 用户ID |
| `login_type` | 登录方式 |
| `login_result` | 登录结果 |
| `login_ip` | 登录IP |

---

## 三、接口实现详情

### GET /api/auth/captcha — 获取图形验证码

> 文件：`LoginController.java`

**请求参数**：无  
**响应**：`Header.captchaId + image/jpeg`

**流程图**：

```text
GET /api/auth/captcha
  → 生成验证码图片
  → 生成 captchaId
  → Redis 写 captcha:{captchaId}，TTL 1分钟
  → Header 返回 captchaId，Body 返回图片流
```

**响应示例**：

```json
{"success": true, "data": {"captchaId": "header-returned"}, "message": null}
```

### POST /api/auth/login — 用户登录

**请求参数**（Body JSON）：`loginId`、`userPassword`、`captchaId`、`captchaCode`

**流程图**：

```text
POST /api/auth/login
  → 校验图形验证码
  → 查询账号/邮箱用户
  → 封号拦截
  → 密码比对
  → 写 token + 登录日志
  → 返回 token
```

**响应示例**：

```json
{"success": true, "data": "token-string", "message": null}
```

### POST /api/auth/register — 用户注册

**请求参数**（Query/Form）：`username`、`email`、`emailCode`、`password`

**流程图**：

```text
POST /api/auth/register
  → 参数与验证码校验
  → 唯一性校验
  → 密码加密入库
  → 返回注册结果
```

**响应示例**：

```json
{"success": true, "data": "注册成功", "message": null}
```

### POST /api/auth/code — 发送邮箱验证码

**请求参数**（Query/Form）：`email`

**流程图**：

```text
POST /api/auth/code
  → 邮箱格式校验
  → 发送频控
  → 发送验证码邮件
  → Redis 写入邮箱验证码
```
