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
                        // 1. 放行 OpenAPI 核心数据接口
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        // 2. 放行 Swagger UI 及其静态资源（核心！）
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/webjars/**",         // <--- 之前缺了这个，导致页面加载不出样式和脚本！
                        // 3. 放行错误兜底路径
                        "/error",
                        "/error/**"
                );
    }
}