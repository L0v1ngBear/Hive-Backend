package my.hive_back.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.tenant.TenantIsolationProperties;
import my.hive.common.tenant.TenantIsolationSupport;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
/**
 * MybatisPlusConfig 属于小程序后端通用能力层，定义框架配置，用于组织基础设施行为。
 */
@Configuration
@EnableConfigurationProperties(TenantIsolationProperties.class)
public class MybatisPlusConfig {

    private static final Set<String> IGNORE_TENANT_TABLES = Set.of(
            "tenant",
            "user",
            "sys_user_role",
            "sys_user_permission",
            "sys_role",
            "sys_role_permission",
            "sys_permission"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantIsolationSupport tenantIsolationSupport) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // DATABASE mode routes by datasource, so tenant_code should no longer be
                // injected into every SQL statement.
                if (!tenantIsolationSupport.useTenantColumn()) {
                    return new NullValue();
                }
                String tenantCode = TenantPermissionContext.getTenantCode();
                if (tenantCode == null) {
                    return new NullValue();
                }
                return new StringValue(tenantCode);
            }

            @Override
            public String getTenantIdColumn() {
                return tenantIsolationSupport.getTenantColumn();
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (tenantIsolationSupport.shouldIgnoreTenantLine()) {
                    return true;
                }
                return IGNORE_TENANT_TABLES.contains(tableName.toLowerCase());
            }
        }));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
