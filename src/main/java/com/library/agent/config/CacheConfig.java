package com.library.agent.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 开启注解式缓存。缓存实现与容量/TTL 策略在 application.yml 的 spring.cache 下配置。
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
