package com.caoyixin.lock.util;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * SpEL表达式解析工具类
 *
 * @author caoyixin
 */
public class SpelUtils {

    /**
     * SpEL表达式解析器
     */
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /**
     * 参数名发现器
     */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * 解析SpEL表达式
     *
     * @param joinPoint      切点信息
     * @param spelExpression SpEL表达式
     * @return 解析结果
     */
    public static Object parseSpel(JoinPoint joinPoint, String spelExpression) {
        // 获取方法签名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();

        // 获取方法参数
        Object[] args = joinPoint.getArgs();

        // 创建评估上下文
        EvaluationContext context = createEvaluationContext(method, args);

        // 解析表达式
        return EXPRESSION_PARSER.parseExpression(spelExpression).getValue(context);
    }

    /**
     * 创建评估上下文
     *
     * @param method 方法
     * @param args   方法参数
     * @return 评估上下文
     */
    private static EvaluationContext createEvaluationContext(Method method, Object[] args) {
        // 创建标准评估上下文
        StandardEvaluationContext context = new MethodBasedEvaluationContext(null, method, args,
                PARAMETER_NAME_DISCOVERER);

        // 设置参数值
        String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        if (Objects.nonNull(parameterNames)) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        return context;
    }
}