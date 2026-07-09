package my.hive_back.common.config;

import jakarta.annotation.Resource;
import my.hive.common.utils.TokenUtil;
import my.hive.common.web.TenantUploadResourceResolver;
import my.hive_back.common.interceptor.TenantInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
/**
 * WebMvcConfig 属于小程序后端通用能力层，定义框架配置，用于组织基础设施行为。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/favicon.ico",
            "/error",
            "/auth/login",
            "/auth/wechat-login",
            "/wechat/subscribe/config"
    };

    @Resource
    private TenantInterceptor tenantInterceptor;

    @Value("${app.cors.allowed-origin-patterns:https://hellohive.top,http://localhost:*,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(resolveAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        TokenUtil.HEADER_RENEWED_TOKEN,
                        TokenUtil.HEADER_RENEWED_EXPIRE_AT,
                        TokenUtil.HEADER_RENEWED_RESPONSE_KEY
                )
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = Path.of(uploadRoot).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation)
                .resourceChain(true)
                .addResolver(new TenantUploadResourceResolver());
    }

    private String[] resolveAllowedOrigins() {
        return allowedOriginPatterns.split("\\s*,\\s*");
    }
}
