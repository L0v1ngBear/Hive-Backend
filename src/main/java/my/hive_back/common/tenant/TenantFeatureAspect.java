package my.hive_back.common.tenant;

import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class TenantFeatureAspect {

    @Resource
    private TenantFeatureService tenantFeatureService;

    @Around("@within(my.hive_back.common.tenant.RequireTenantFeature) || @annotation(my.hive_back.common.tenant.RequireTenantFeature)")
    public Object requireFeature(ProceedingJoinPoint joinPoint) throws Throwable {
        RequireTenantFeature requireTenantFeature = resolveFeatureAnnotation(joinPoint);
        if (requireTenantFeature == null) {
            return joinPoint.proceed();
        }
        tenantFeatureService.requireFeatureEnabled(
                TenantPermissionContext.getTenantCode(),
                requireTenantFeature.value(),
                requireTenantFeature.message()
        );
        return joinPoint.proceed();
    }

    private RequireTenantFeature resolveFeatureAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireTenantFeature annotation = AnnotationUtils.findAnnotation(method, RequireTenantFeature.class);
        if (annotation != null) {
            return annotation;
        }
        Object target = joinPoint.getTarget();
        return target == null ? null : AnnotationUtils.findAnnotation(target.getClass(), RequireTenantFeature.class);
    }
}
