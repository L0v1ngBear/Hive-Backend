package my.hive_back.common.interceptor;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.sys.model.mapper.SysRoleMapper;
import my.hive_back.module.sys.model.mapper.SysUserRoleMapper;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.*;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Resource
    private TenantMapper tenantMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取租户编码（保留你原有逻辑）
        String tenantCode = request.getHeader("Tenant-Code");

        String userIdStr = request.getHeader("User-Id");

        // 2. 租户编码为空校验（保留你原有逻辑）
        if (StringUtils.isBlank(tenantCode)) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限：缺少租户编码");
            return false;
        }

        // 3. 用户ID为空校验（新增）
        if (StringUtils.isBlank(userIdStr)) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限：缺少用户ID");
            return false;
        }

        // 4. 租户ID格式校验（补充你TODO的逻辑）
        try {
            // 如果你tenantCode是字符串类型，可校验格式（比如字母+数字+下划线）
            if (!tenantCode.matches("^[a-zA-Z0-9_]+$")) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限：租户编码格式非法");
                return false;
            }
            // 如果你userId是Long类型，解析并校验
            Long userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, 400, "无权限：用户ID格式非法");
            return false;
        }

        // 5. 校验租户是否存在（保留你原有逻辑）
        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, 403, "无权限访问：租户不存在");
            return false;
        }

        // 6. 新增：查询用户在当前租户下的权限（核心逻辑）
        Long userId = Long.parseLong(userIdStr);
        Set<String> permCodes = getUserPermCodes(tenantCode, userId);

        // 7. 权限为空校验（新增）
        if (CollectionUtils.isEmpty(permCodes)) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, 403, "无权限：用户在当前租户下无任何权限");
            return false;
        }

        TenantPermissionContext.init(tenantCode, userId, permCodes); // 新增：初始化权限上下文

        return true;
    }

    /**
     * 新增：查询用户在指定租户下的权限编码集合
     */
    private Set<String> getUserPermCodes(String tenantCode, Long userId) {
        Set<String> permCodes = new HashSet<>();

        // 步骤1：查询用户在当前租户下绑定的角色编码
        List<String> roleCodes = sysUserRoleMapper.selectRoleCodesByUserAndTenant(userId, tenantCode);
        if (CollectionUtils.isEmpty(roleCodes)) {
            return permCodes;
        }

        // 步骤2：批量查询角色对应的权限编码（逗号分隔）
        List<String> rolePermStrList = sysRoleMapper.selectPermCodesByTenantAndRoleCodes(tenantCode, roleCodes);
        if (CollectionUtils.isEmpty(rolePermStrList)) {
            return permCodes;
        }

        // 步骤3：拆分逗号分隔的权限编码，聚合为Set
        for (String permStr : rolePermStrList) {
            if (StringUtils.isNotBlank(permStr)) {
                String[] arr = permStr.split(",");
                permCodes.addAll(Arrays.asList(arr));
            }
        }

        return permCodes;
    }

    /**
     * 保留你原有：统一写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, HttpStatus httpStatus,
                                    Integer bizCode, String msg) throws Exception {
        ResultDTO<Void> errorResult = ResultDTO.fail(bizCode, msg);
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(httpStatus.value());
        try (var writer = response.getWriter()) {
            objectMapper.writeValue(writer, errorResult);
            writer.flush();
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantPermissionContext.clear();
    }
}