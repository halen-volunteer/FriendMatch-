package com.zero.usercenter.aop;

import com.zero.usercenter.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限切面表达式解析器。
 * 当前只负责从方法参数中解析 `teamId` 一类简单上下文，避免把参数定位逻辑散落到各个切面里。
 */
@Component
public class PermissionExpressionEvaluator {

    /**
     * SpEL 表达式解析器。
     * 用于把注解里的 `#p0`、`#a1` 这类表达式解析成真实参数值。
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 解析切面当前命中的最具体方法。
     */
    public Method resolveMethod(ProceedingJoinPoint joinPoint) {
        // 1. 先从切点签名拿到声明方法，再解析成代理目标类上的最具体实现方法。
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
    }

    /**
     * 按 SpEL 表达式从方法参数中提取 Long 值。
     *
     * @param expression 注解里定义的 SpEL 表达式
     * @param args 当前目标方法的参数数组
     * @param invalidMessage 解析失败时抛出的错误信息
     * @return 解析得到的 Long 类型结果
     */
    public Long getLongValue(String expression, Object[] args, String invalidMessage) {
        // 1. 先统一走底层表达式解析，拿到原始对象值。
        Object value = getValue(expression, args, invalidMessage);
        if (value == null) {
            throw new BusinessException(invalidMessage);
        }

        // 2. 若结果本身就是数字类型，直接转换为 Long 返回。
        if (value instanceof Number number) {
            return number.longValue();
        }

        // 3. 否则退化为字符串转换，兼容 teamId 以字符串形式传入的场景。
        try {
            return Long.valueOf(value.toString());
        } catch (Exception e) {
            throw new BusinessException(invalidMessage);
        }
    }

    /**
     * 按 SpEL 表达式从方法参数中提取字符串值。
     *
     * @param expression 注解里定义的 SpEL 表达式
     * @param args 当前目标方法的参数数组
     * @param invalidMessage 解析失败时抛出的错误信息
     * @return 解析得到的非空白字符串结果
     */
    public String getStringValue(String expression, Object[] args, String invalidMessage) {
        // 1. 先解析出原始值，再统一做非空和空白校验。
        Object value = getValue(expression, args, invalidMessage);
        if (value == null) {
            throw new BusinessException(invalidMessage);
        }
        String result = value.toString();
        // 2. 解析结果为空白时，同样按无效参数处理。
        if (result.isBlank()) {
            throw new BusinessException(invalidMessage);
        }
        return result;
    }

    /**
     * 按 SpEL 表达式从方法参数中提取原始值。
     *
     * @param expression 注解里定义的 SpEL 表达式
     * @param args 当前目标方法的参数数组
     * @param invalidMessage 解析失败时抛出的错误信息
     * @return 表达式解析得到的原始结果对象
     */
    private Object getValue(String expression, Object[] args, String invalidMessage) {
        // 1. 表达式本身为空时没有继续解析的意义，直接按配置提示抛错。
        if (expression == null || expression.isBlank()) {
            throw new BusinessException(invalidMessage);
        }
        try {
            // 2. 把方法参数按 p0/a0、p1/a1 这种 Spring AOP 常见别名放入上下文，方便注解里引用。
            StandardEvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
            }

            // 3. 使用统一解析器执行表达式，拿到最终的参数值。
            return parser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            throw new BusinessException(invalidMessage);
        }
    }
}
