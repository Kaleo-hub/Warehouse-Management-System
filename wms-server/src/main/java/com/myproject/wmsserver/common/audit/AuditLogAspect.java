package com.myproject.wmsserver.common.audit;

import com.myproject.wmsserver.common.audit.entity.AuditLogRecord;
import com.myproject.wmsserver.common.audit.mapper.AuditLogMapper;
import com.myproject.wmsserver.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审计日志切面：所有【写接口】自动留痕，不用在每个方法上贴注解。
 *
 * ========== 为什么不在每个方法上标 @AuditLog ==========
 * 注解式的问题不是麻烦，而是【会漏】：
 * 新加一个写接口忘了标注解，那条链路就静默地没有审计 —— 而且没人会发现，
 * 直到出了事需要查是谁改的时候才反应过来。
 * 这里按「控制器 + POST/PUT/DELETE 映射」自动切，新增接口天然被覆盖。
 *
 * ========== 为什么只记写接口 ==========
 * 查询接口量大、没有变更语义，全记下来只会把表撑爆、把真正要看的变更淹没。
 *
 * ========== 失败为什么也要记 ==========
 * 「谁试图改但没改成」往往比成功更值得看（越权尝试、前端重复提交、
 * 业务规则拦截导致的误操作），所以异常分支同样落库，并记下原因。
 *
 * ========== 为什么不异步 ==========
 * 异步要么丢日志（线程池满了），要么得引入 MQ（第 8 周的事）。
 * 现在是同步写一条，几毫秒；而且【日志写失败绝不影响业务】——
 * 整段包在 try-catch 里，最坏情况只是少一条审计记录。
 */
@Slf4j
@Aspect
@Component
@Order(10)
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final int MAX_MODULE = 32;
    private static final int MAX_ACTION = 32;
    private static final int MAX_TARGET_TYPE = 32;
    private static final int MAX_TARGET_ID = 64;
    private static final int MAX_IP = 64;
    private static final int MAX_ERROR = 255;

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Around("execution(public * com.myproject.wmsserver..controller..*(..)) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object returnValue = null;
        Throwable error = null;
        try {
            returnValue = pjp.proceed();
            return returnValue;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            writeLog(pjp, returnValue, error, System.currentTimeMillis() - start);
        }
    }

    private void writeLog(ProceedingJoinPoint pjp, Object returnValue, Throwable error, long costMs) {
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            HttpServletRequest request = currentRequest();

            AuditLogRecord row = new AuditLogRecord();
            row.setTraceId(MDC.get("traceId"));
            row.setUserId(UserContext.getUserId());
            UserContext.CurrentUser user = UserContext.get();
            row.setUserName(user == null ? "" : nvl(user.getUsername()));
            row.setModule(truncate(moduleOf(request), MAX_MODULE));
            row.setAction(truncate(actionOf(request, signature), MAX_ACTION));
            row.setTargetType(truncate(targetTypeOf(signature), MAX_TARGET_TYPE));
            row.setTargetId(truncate(targetIdOf(pjp), MAX_TARGET_ID));
            row.setDetail(detailOf(pjp));
            row.setIp(truncate(clientIp(request), MAX_IP));
            row.setResult(error == null ? 1 : 0);
            row.setErrorMsg(error == null ? null : truncate(error.getMessage(), MAX_ERROR));
            row.setCostMs(costMs);

            auditLogMapper.insert(row);
        } catch (Exception e) {
            /*
             * 【审计失败绝不影响业务】
             * 业务已经做完了，不能因为「日志没写进去」把成功的请求变成 500。
             * 这里只留 warn：如果真的频繁失败，日志里看得见。
             */
            log.warn("写审计日志失败", e);
        }
    }

    /** 模块名取 URL 的第一段：/api/v1/inbound/inbound-orders → inbound */
    private String moduleOf(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String uri = request.getRequestURI();
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("v1".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return parts.length > 1 ? parts[1] : "";
    }

    private String actionOf(HttpServletRequest request, MethodSignature signature) {
        String method = request == null ? "" : request.getMethod() + " ";
        return method + signature.getMethod().getName();
    }

    /** InboundOrderController → InboundOrder */
    private String targetTypeOf(MethodSignature signature) {
        String simpleName = signature.getDeclaringType().getSimpleName();
        return simpleName.endsWith("Controller")
                ? simpleName.substring(0, simpleName.length() - "Controller".length())
                : simpleName;
    }

    /** 参数里第一个数字（通常是路径上的 {id}）当作操作对象 id */
    private String targetIdOf(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof Long || arg instanceof Integer) {
                return String.valueOf(arg);
            }
        }
        return null;
    }

    private String detailOf(ProceedingJoinPoint pjp) {
        try {
            List<Object> args = new ArrayList<>();
            for (Object arg : pjp.getArgs()) {
                if (arg == null) {
                    continue;
                }
                String type = arg.getClass().getName();
                // 请求/响应对象、校验结果这些不该进审计
                if (type.startsWith("jakarta.servlet") || type.startsWith("org.springframework.web")
                        || type.startsWith("org.springframework.validation")) {
                    continue;
                }
                args.add(arg);
            }
            // 先转成 Map/List 再脱敏：对象树用普通集合表达，遍历和改写都不用碰 Jackson 的节点 API
            Object tree = objectMapper.convertValue(args, Object.class);
            maskPassword(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (JacksonException e) {
            log.warn("审计参数序列化失败，本条审计不记 detail", e);
            return null;
        }
    }

    /**
     * 密码类字段脱敏。
     * 审计日志的价值是「查得出谁改了什么」，不是「把密码抄一份存起来」——
     * 明文密码进了审计表，等于凭空多了一个泄露点。
     */
    @SuppressWarnings("unchecked")
    private void maskPassword(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : new ArrayList<>(map.entrySet())) {
                String key = String.valueOf(entry.getKey());
                if (key.toLowerCase().contains("password")) {
                    ((Map<String, Object>) map).put(key, "***");
                } else {
                    maskPassword(entry.getValue());
                }
            }
        } else if (node instanceof List<?> list) {
            list.forEach(this::maskPassword);
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // 代理链是 "客户端, 代理1, 代理2"，第一个才是真实客户端
            return forwarded.split(",")[0].trim();
        }
        return nvl(request.getRemoteAddr());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}