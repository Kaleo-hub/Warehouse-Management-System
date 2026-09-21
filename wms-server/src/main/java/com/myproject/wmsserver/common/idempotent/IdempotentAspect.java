package com.myproject.wmsserver.common.idempotent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等切面：把 @Idempotent 标注的方法包起来。
 *
 * ========== 一次请求的完整流程 ==========
 *   ① 拼 key（bizType + 客户端key/业务键 + 请求体哈希）
 *   ② 抢位（靠唯一索引）
 *        ├─ 抢到 → 执行业务 → 成功写快照 / 失败记原因 → 返回
 *        ├─ 命中成功记录 → 直接把上次的响应快照还回去（业务不执行）
 *        ├─ 命中处理中 → 抛 10007「请勿重复提交」
 *        └─ 命中失败记录 → 放行重试
 *
 * ========== 为什么切面只做「抠参数」，状态机放在 Service ==========
 * 切面里塞满 if (status == ...) 会很难读也很难测；
 * 状态判断全部在 IdempotentService 里，切面只负责翻译。
 *
 * ========== 顺序 ==========
 * 审计切面（@Order(10)）在外层，幂等切面（@Order(20)）在内层。
 * 这样被幂等拦下来的重复请求【也会进审计日志】——
 * 「谁在什么时候重复提交了什么」本身就是有价值的证据，不该被悄悄丢掉。
 */
@Slf4j
@Aspect
@Component
@Order(20)
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String HEADER_KEY = "X-Idempotent-Key";
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();
    /** SpEL 解析一次就够，别每次请求都重新编译表达式 */
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    private final IdempotentService idempotentService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = buildKey(pjp, idempotent);

        AcquireResult result = idempotentService.tryAcquire(
                key, idempotent.bizType(), hashArgs(pjp), idempotent.ttlMinutes());

        switch (result.status()) {
            case REPLAY -> {
                // 上次已经成功过了：原样把那份响应还回去，业务一行都不执行
                return replay(result.snapshot(), ((MethodSignature) pjp.getSignature()).getReturnType());
            }
            case PROCESSING -> throw new BizException(CommonErrorCode.IDEMPOTENT_CONFLICT);
            case RETRY, ACQUIRED -> {
                // 继续往下执行（RETRY 是「上次失败了，这次放行」）
            }
        }

        try {
            Object returnValue = pjp.proceed();
            idempotentService.markSuccess(key, returnValue);
            return returnValue;
        } catch (Throwable e) {
            /*
             * 失败也要落一条 status=2，两个原因：
             *   1. 下次同一个 key 再来能放行重试（而不是被永久挡住）
             *   2. 排查时能看到「上次失败在哪」
             * 注意这里【不能吞异常】，业务为什么失败要原样抛给调用方和全局异常处理器。
             */
            idempotentService.markFailed(key, e.getMessage());
            throw e;
        }
    }

    /**
     * 拼幂等键：bizType + 标识 + 请求体哈希。
     *
     * 标识的优先级：
     *   1. 请求头 X-Idempotent-Key —— 前端在「打开新建页」时生成一个 UUID，双击/重试都用它
     *   2. @Idempotent(key = "#orderId") 的 SpEL 结果 —— 业务键，不依赖客户端配合
     *   3. anon —— 既没有 header 也没有业务键（只靠请求体哈希去重）
     *
     * 【为什么最后一定要拼请求体哈希】
     * 同一个业务键下，「内容不同的两次请求」是两件事：
     * 比如分批收货——收 30 件、再收 30 件，必须都能执行。
     * 只有「内容完全相同」的重复提交才是要挡的，哈希正好表达这件事。
     */
    private String buildKey(ProceedingJoinPoint pjp, Idempotent idempotent) {
        String identity = resolveIdentity(pjp, idempotent);
        return idempotent.bizType() + ":" + identity + ":" + hashArgs(pjp);
    }

    private String resolveIdentity(ProceedingJoinPoint pjp, Idempotent idempotent) {
        String headerKey = currentHeader(HEADER_KEY);
        if (StringUtils.hasText(headerKey)) {
            return headerKey.trim();
        }
        if (StringUtils.hasText(idempotent.key())) {
            Object value = evaluateKey(pjp, idempotent.key());
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "anon";
    }

    private Object evaluateKey(ProceedingJoinPoint pjp, String expression) {
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            Expression parsed = EXPRESSION_CACHE.computeIfAbsent(expression, PARSER::parseExpression);
            StandardEvaluationContext context =
                    new StandardEvaluationContext(pjp.getTarget());
            String[] names = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
            Object[] args = pjp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    context.setVariable(names[i], args[i]);
                }
            }
            return parsed.getValue(context);
        } catch (Exception e) {
            // 表达式写错了不该让接口挂掉：退化成「只靠请求体哈希去重」，并留一条日志
            log.warn("幂等 key 表达式解析失败 expression={}", expression, e);
            return null;
        }
    }

    /**
     * 请求参数哈希。只做「相等判断」，不做安全用途，所以用 SHA-256 而不是 MD5（项目里没别的约束，选强的）。
     */
    private String hashArgs(ProceedingJoinPoint pjp) {
        try {
            String json = objectMapper.writeValueAsString(serializableArgs(pjp.getArgs()));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | JacksonException e) {
            // 哈希不出来就当每次都不一样（退化成不去重），但绝不能因此让业务失败
            log.warn("幂等请求哈希计算失败，本次不参与去重", e);
            return "unknown";
        }
    }

    /** 过滤掉没法（也不该）序列化的参数 */
    private List<Object> serializableArgs(Object[] args) {
        List<Object> list = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            String type = arg.getClass().getName();
            if (type.startsWith("jakarta.servlet") || type.startsWith("org.springframework.web")) {
                continue;
            }
            list.add(arg);
        }
        return list;
    }

    /**
     * 重放上次的响应。
     *
     * 【必须按方法的真实返回类型反序列化，不能图省事用 Object.class】
     * 切面包着的是 Result<Long> create(...) 这样的方法，Spring 的代理
     * 会把环绕通知的返回值按声明类型做强转。
     * 用 Object.class 反序列化得到的是 LinkedHashMap，
     * 一返回就炸：ClassCastException: LinkedHashMap cannot be cast to Result（实测踩到）。
     * 按声明类型反序列化，还原出来的就是真正的 Result 对象。
     */
    private Object replay(String snapshot, Class<?> returnType) {
        if (!StringUtils.hasText(snapshot)) {
            // 记录里没有快照（比如上次序列化失败），只能告诉调用方「这个请求已经成功过了」
            throw new BizException(CommonErrorCode.IDEMPOTENT_CONFLICT);
        }
        if (returnType == null || returnType.isInterface()) {
            // 理论上不会发生（控制器都返回具体的 Result），真遇到了只能拒绝重放
            log.warn("幂等重放的返回类型不支持 returnType={}", returnType);
            throw new BizException(CommonErrorCode.IDEMPOTENT_CONFLICT);
        }
        return objectMapper.readValue(snapshot, returnType);
    }

    private String currentHeader(String name) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request == null ? null : request.getHeader(name);
        }
        return null;
    }
}