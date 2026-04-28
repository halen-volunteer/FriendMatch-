# Mapper XML 配置完整指南

## 目录
1. [文件结构](#文件结构)
2. [配置说明](#配置说明)
3. [MyBatis-Plus 自动映射](#mybatis-plus-自动映射)
4. [自定义 SQL 示例](#自定义-sql-示例)
5. [常见问题](#常见问题)

---

## 文件结构

```
src/main/resources/Mapper/
├── UserMapper.xml              ✅ 用户表 Mapper
├── UserFriendMapper.xml        ✅ 好友关系 Mapper
├── UserBlacklistMapper.xml     ✅ 黑名单 Mapper
└── UserLoginLogMapper.xml      ✅ 登录日志 Mapper
```

**总计**：4 个 XML 配置文件，全部已创建

---

## 配置说明

### 1. UserMapper.xml

**对应 Mapper 接口**：`com.zero.usercenter.Mapper.UserMapper`

**对应数据库表**：`t_user`

**功能**：用户基本信息管理

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zero.usercenter.Mapper.UserMapper">
    <!-- 依赖 MyBatis-Plus 自动驼峰转下划线映射 -->
</mapper>
```

**自动支持的操作**：
- ✅ `insert()` - 插入用户
- ✅ `deleteById()` - 删除用户
- ✅ `updateById()` - 更新用户
- ✅ `selectById()` - 查询单个用户
- ✅ `selectList()` - 查询用户列表
- ✅ `selectPage()` - 分页查询

---

### 2. UserFriendMapper.xml

**对应 Mapper 接口**：`com.zero.usercenter.Mapper.UserFriendMapper`

**对应数据库表**：`t_user_friend`

**功能**：好友关系管理

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zero.usercenter.Mapper.UserFriendMapper">
    <!-- 依赖 MyBatis-Plus 自动驼峰转下划线映射 -->
</mapper>
```

**自动支持的操作**：
- ✅ `insert()` - 添加好友关系
- ✅ `deleteById()` - 删除好友关系
- ✅ `updateById()` - 更新好友关系
- ✅ `selectById()` - 查询单个好友关系
- ✅ `selectList()` - 查询好友列表
- ✅ `selectPage()` - 分页查询好友

---

### 3. UserBlacklistMapper.xml

**对应 Mapper 接口**：`com.zero.usercenter.Mapper.UserBlacklistMapper`

**对应数据库表**：`t_user_blacklist`

**功能**：黑名单管理

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zero.usercenter.Mapper.UserBlacklistMapper">
    <!-- 依赖 MyBatis-Plus 自动驼峰转下划线映射 -->
</mapper>
```

**自动支持的操作**：
- ✅ `insert()` - 添加黑名单
- ✅ `deleteById()` - 删除黑名单
- ✅ `updateById()` - 更新黑名单
- ✅ `selectById()` - 查询单个黑名单
- ✅ `selectList()` - 查询黑名单列表
- ✅ `selectPage()` - 分页查询黑名单

---

### 4. UserLoginLogMapper.xml

**对应 Mapper 接口**：`com.zero.usercenter.Mapper.UserLoginLogMapper`

**对应数据库表**：`t_user_login_log`

**功能**：登录日志管理

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zero.usercenter.Mapper.UserLoginLogMapper">
    <!-- 依赖 MyBatis-Plus 自动驼峰转下划线映射 -->
</mapper>
```

**自动支持的操作**：
- ✅ `insert()` - 插入登录日志
- ✅ `deleteById()` - 删除登录日志
- ✅ `updateById()` - 更新登录日志
- ✅ `selectById()` - 查询单个日志
- ✅ `selectList()` - 查询日志列表
- ✅ `selectPage()` - 分页查询日志

---

## MyBatis-Plus 自动映射

### 驼峰命名转换

MyBatis-Plus 已配置自动驼峰转下划线映射，无需手写 ResultMap。

**映射规则**：

| Java 属性 | 数据库列 | 说明 |
|----------|---------|------|
| `userId` | `user_id` | 自动转换 |
| `userName` | `user_name` | 自动转换 |
| `userEmail` | `user_email` | 自动转换 |
| `createTime` | `create_time` | 自动转换 |
| `updateTime` | `update_time` | 自动转换 |

### 配置位置

文件：`src/main/resources/application.yaml`

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.zero.usercenter.Model
  configuration:
    map-underscore-to-camel-case: true  # 启用驼峰转换
```

---

## 自定义 SQL 示例

### 场景 1：复杂查询

如果需要在 UserFriendMapper 中添加自定义查询方法：

```java
// UserFriendMapper.java
public interface UserFriendMapper extends BaseMapper<UserFriend> {
    /**
     * 查询用户的所有好友（包括好友信息）
     */
    List<UserFriend> selectFriendsWithInfo(@Param("userId") Long userId);
}
```

对应的 XML 配置：

```xml
<!-- UserFriendMapper.xml -->
<mapper namespace="com.zero.usercenter.Mapper.UserFriendMapper">
    
    <!-- 自定义查询：获取用户的所有好友及其信息 -->
    <select id="selectFriendsWithInfo" resultType="com.zero.usercenter.Model.UserFriend">
        SELECT 
            uf.id,
            uf.user_id,
            uf.friend_id,
            uf.friend_remark,
            uf.friend_status,
            uf.create_time,
            uf.agree_time,
            uf.update_time
        FROM t_user_friend uf
        WHERE uf.user_id = #{userId}
        AND uf.friend_status = 1
        ORDER BY uf.create_time DESC
    </select>
    
</mapper>
```

### 场景 2：批量操作

在 UserLoginLogMapper 中添加批量插入：

```java
// UserLoginLogMapper.java
public interface UserLoginLogMapper extends BaseMapper<UserLoginLog> {
    /**
     * 批量插入登录日志
     */
    int insertBatch(@Param("logs") List<UserLoginLog> logs);
}
```

对应的 XML 配置：

```xml
<!-- UserLoginLogMapper.xml -->
<mapper namespace="com.zero.usercenter.Mapper.UserLoginLogMapper">
    
    <!-- 批量插入登录日志 -->
    <insert id="insertBatch">
        INSERT INTO t_user_login_log (user_id, login_ip, login_type, login_result, create_time)
        VALUES
        <foreach collection="logs" item="log" separator=",">
            (#{log.userId}, #{log.loginIp}, #{log.loginType}, #{log.loginResult}, #{log.createTime})
        </foreach>
    </insert>
    
</mapper>
```

### 场景 3：统计查询

在 UserBlacklistMapper 中添加统计方法：

```java
// UserBlacklistMapper.java
public interface UserBlacklistMapper extends BaseMapper<UserBlacklist> {
    /**
     * 统计用户被拉黑的次数
     */
    Integer countBlacklistByUserId(@Param("userId") Long userId);
}
```

对应的 XML 配置：

```xml
<!-- UserBlacklistMapper.xml -->
<mapper namespace="com.zero.usercenter.Mapper.UserBlacklistMapper">
    
    <!-- 统计用户被拉黑的次数 -->
    <select id="countBlacklistByUserId" resultType="java.lang.Integer">
        SELECT COUNT(*)
        FROM t_user_blacklist
        WHERE black_user_id = #{userId}
        AND is_delete = 0
    </select>
    
</mapper>
```

---

## 常见问题

### Q1：为什么 XML 文件是空的？

**A**：因为所有 Mapper 都继承了 `BaseMapper<T>`，MyBatis-Plus 已经自动生成了所有基础 CRUD 操作的 SQL。

XML 文件只需要在以下情况下添加内容：
- 需要自定义 SQL 查询
- 需要复杂的 JOIN 操作
- 需要批量操作
- 需要动态 SQL

### Q2：如何验证 Mapper XML 配置是否正确？

**A**：启动应用后，查看日志：

```
2024-03-16 10:30:45.123  INFO  --- [main] o.m.s.mapper.ClassPathMapperScanner      : Scanned mapper: 'com.zero.usercenter.Mapper.UserMapper'
2024-03-16 10:30:45.124  INFO  --- [main] o.m.s.mapper.ClassPathMapperScanner      : Scanned mapper: 'com.zero.usercenter.Mapper.UserFriendMapper'
2024-03-16 10:30:45.125  INFO  --- [main] o.m.s.mapper.ClassPathMapperScanner      : Scanned mapper: 'com.zero.usercenter.Mapper.UserBlacklistMapper'
2024-03-16 10:30:45.126  INFO  --- [main] o.m.s.mapper.ClassPathMapperScanner      : Scanned mapper: 'com.zero.usercenter.Mapper.UserLoginLogMapper'
```

如果看到这些日志，说明 Mapper 已正确扫描。

### Q3：如何在 XML 中使用动态 SQL？

**A**：使用 `<if>`、`<choose>`、`<foreach>` 等标签：

```xml
<select id="selectByCondition" resultType="com.zero.usercenter.Model.User">
    SELECT * FROM t_user
    <where>
        <if test="userId != null">
            AND id = #{userId}
        </if>
        <if test="userEmail != null">
            AND user_email = #{userEmail}
        </if>
        <if test="userNickname != null">
            AND user_nickname LIKE CONCAT('%', #{userNickname}, '%')
        </if>
    </where>
</select>
```

### Q4：XML 文件位置不对会怎样？

**A**：应用启动时会报错：

```
Error creating bean with name 'userMapper': Invocation of init method failed; 
nested exception is java.io.FileNotFoundException: 
Could not find resource classpath*:/mapper/**/*.xml
```

**解决方案**：
1. 确保 XML 文件在 `src/main/resources/Mapper/` 目录下
2. 检查 `application.yaml` 中的 `mapper-locations` 配置
3. 确保 XML 文件名与 Mapper 接口名对应

### Q5：如何添加新的自定义方法？

**A**：三步操作：

**第 1 步**：在 Mapper 接口中定义方法

```java
public interface UserMapper extends BaseMapper<User> {
    User selectByEmail(@Param("email") String email);
}
```

**第 2 步**：在 XML 中实现 SQL

```xml
<select id="selectByEmail" resultType="com.zero.usercenter.Model.User">
    SELECT * FROM t_user WHERE user_email = #{email}
</select>
```

**第 3 步**：在 Service 中使用

```java
@Service
public class UserServiceImpl {
    @Resource
    private UserMapper userMapper;
    
    public User getUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }
}
```

---

## 最佳实践

### ✅ 推荐做法

1. **保持 XML 简洁**
   - 只在 XML 中写复杂 SQL
   - 简单查询使用 MyBatis-Plus 的 QueryWrapper

2. **使用参数化查询**
   ```xml
   <!-- ✅ 正确 -->
   <select id="selectByEmail">
       SELECT * FROM t_user WHERE user_email = #{email}
   </select>
   
   <!-- ❌ 错误（SQL 注入风险） -->
   <select id="selectByEmail">
       SELECT * FROM t_user WHERE user_email = '${email}'
   </select>
   ```

3. **使用 @Param 注解**
   ```java
   // ✅ 正确
   List<User> selectByCondition(@Param("email") String email, @Param("status") Integer status);
   
   // ❌ 不推荐
   List<User> selectByCondition(String email, Integer status);
   ```

4. **添加注释**
   ```xml
   <!-- 查询用户及其好友信息 -->
   <select id="selectUserWithFriends">
       ...
   </select>
   ```

### ❌ 避免做法

1. 在 XML 中使用字符串拼接（SQL 注入风险）
2. 在 XML 中写过于复杂的逻辑（应该在 Service 层处理）
3. 忘记添加 `@Param` 注解（参数绑定失败）
4. 使用错误的 resultType（类型转换失败）

---

## 总结

| 文件 | 状态 | 说明 |
|------|------|------|
| UserMapper.xml | ✅ 已创建 | 用户表基础操作 |
| UserFriendMapper.xml | ✅ 已创建 | 好友关系基础操作 |
| UserBlacklistMapper.xml | ✅ 已创建 | 黑名单基础操作 |
| UserLoginLogMapper.xml | ✅ 已创建 | 登录日志基础操作 |

**所有 Mapper XML 配置已完成！**

- ✅ 4 个 XML 文件已创建
- ✅ 自动驼峰转换已配置
- ✅ 基础 CRUD 操作已支持
- ✅ 可随时添加自定义 SQL

如需添加自定义 SQL 查询，参考上面的示例即可。
