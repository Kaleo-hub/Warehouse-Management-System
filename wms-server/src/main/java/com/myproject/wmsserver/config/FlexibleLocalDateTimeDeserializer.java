package com.myproject.wmsserver.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 宽松的 LocalDateTime 反序列化器。
 *
 * 【为什么需要它】
 * JacksonConfig 里只注册了 LocalDateTimeSerializer，接口【输出】的是 "2026-09-20 09:00:00"。
 * 但 Jackson 默认的 LocalDateTime 反序列化器只认 ISO-8601 的 "2026-09-20T09:00:00"。
 * 结果就是：前端拿到什么格式就回传什么格式 → 建单接口直接报
 * "请求体格式错误"（10003）。API 输出什么就应该能接收什么，两边必须对称。
 *
 * 这里同时兼容三种写法：
 *   2026-09-20 09:00:00        接口输出格式（前端 el-date-picker 用的就是它）
 *   2026-09-20T09:00:00        ISO-8601，兼容 Postman / Swagger 的默认写法
 *   2026-09-20T09:00:00.123    带毫秒，截掉即可
 */
public class FlexibleLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    private static final DateTimeFormatter SPACE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {

        String raw = parser.getString();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String text = raw.trim().replace('T', ' ');

        // 去掉毫秒/纳秒：2026-09-20 09:00:00.123456 → 2026-09-20 09:00:00
        int dot = text.indexOf('.');
        if (dot > 0) {
            text = text.substring(0, dot);
        }
        // 防御性截断（时区后缀等），长度超过 19 的部分一律不要
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }

        try {
            return LocalDateTime.parse(text, SPACE_FORMAT);
        } catch (DateTimeParseException e) {
            // 用 handleWeirdStringValue 抛，Spring 才能转成 400 + 10003，
            // 直接抛 DateTimeParseException 会变成 500 系统异常，前端拿不到有用信息
            return (LocalDateTime) context.handleWeirdStringValue(
                    LocalDateTime.class, raw,
                    "时间格式应为 yyyy-MM-dd HH:mm:ss 或 ISO-8601（yyyy-MM-ddTHH:mm:ss）");
        }
    }
}