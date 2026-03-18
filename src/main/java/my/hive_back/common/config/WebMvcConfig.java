package my.hive_back.common.config;

import jakarta.annotation.Resource;
import my.hive_back.common.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 1. OpenAPI 核心接口（精确 + 通配符）
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        // 2. Swagger UI 页面（精确 + 通配符 + 根路径）
                        "/swagger-ui",
                        "/swagger-ui/**",
                        "/swagger-ui/index.html",
                        "/swagger-ui.html",
                        // 3. 错误转发路径（避免连锁拦截）
                        "/error",
                        "/error/**"
                );// 拦截所有路径（包含 /api 上下文）

    }
}