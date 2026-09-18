package com.myproject.wmsserver.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * LocalDateTime 全局统一成 yyyy-MM-dd HH:mm:ss，和接口文档里 createTime 的格式一致。
     *
     * 【序列化器和反序列化器必须成对注册】
     * 只加 Serializer 的话，接口输出是 "2026-09-20 09:00:00"，
     * 但接收时 Jackson 仍按 ISO-8601 解析，前端把输出原样回传就会 10003 请求体格式错误。
     * 这是最容易漏的一处 —— 输出格式和输入格式要对称。
     */
    @Bean
    public JsonMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> builder.addModule(new SimpleModule()
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME))
                .addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer()));
    }
}