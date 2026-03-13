package my.hive_back.common.aop;

import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.context.TenantPermissionContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;


@Aspect
@Component
public class PermissionAspect {

    @Pointcut("@annotation(my.hive_back.common.annotation.RequirePermission)")
    public void permissionPointcut() {}

    @Around("permissionPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        String permCode = annotation.value();
        String message = annotation.message();

        // 2. 权限校验
        if (!TenantPermissionContext.hasPermission(permCode)) {
            throw new AccessDeniedException(message);
        }

        // 3. 执行原方法
        return joinPoint.proceed();
    }
}