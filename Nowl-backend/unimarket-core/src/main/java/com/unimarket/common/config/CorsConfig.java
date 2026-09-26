package com.unimarket.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置类
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(@Qualifier("corsConfigurationSource") CorsConfigurationSource source) {
        // 使用 SecurityConfig 的来源名单，避免两层 CORS 对同一域名给出不同结果。
        return new CorsFilter(source);
    }
}
