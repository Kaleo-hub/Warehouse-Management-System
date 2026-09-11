package com.myproject.wmsserver.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.myproject.wmsserver.**.mapper")
public class MybatisPlusConfig {
}
