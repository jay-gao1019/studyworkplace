package com.studdy.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射（docs/10 §1）：种子 JSON 随 jar 打包，经 /seed/*.json 伺服；
 * 前端构建产物置于 resources/static，由 Boot 默认伺服。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/seed/**")
                .addResourceLocations("classpath:/seed/")
                .setCachePeriod(3600);
    }
}
