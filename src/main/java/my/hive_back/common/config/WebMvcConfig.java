package my.hive_back.common.config;

import jakarta.annotation.Resource;
import my.hive_back.common.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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
                        // 1. 放行 OpenAPI 核心数据接口 (SpringDoc 默认路径)
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        // 2. 放行 Swagger UI 及其静态资源
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/webjars/**",
                        // 3. 必须放行静态资源映射
                        "/favicon.ico",
                        // 4. 错误路径
                        "/error",
                        "/error/**"
                )
                .excludePathPatterns("/**/swagger-ui/**", "/**/v3/api-docs/**", "/**/webjars/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 swagger-ui 路径
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/5.10.3/"); // 注意检查你 webjars 里的具体版本
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}