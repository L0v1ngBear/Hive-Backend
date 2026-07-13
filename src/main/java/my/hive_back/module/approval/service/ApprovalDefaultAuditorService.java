package my.hive_back.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.approval.mapper.ApprovalDefaultAuditorMapper;
import my.hive_back.module.approval.model.entity.ApprovalDefaultAuditor;
import my.hive_back.module.approval.model.vo.ApprovalAuditorOptionVO;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ApprovalDefaultAuditorService {

    public static final String TYPE_LEAVE = "LEAVE";
    public static final String TYPE_FINANCE = "FINANCE";
    public static final String TYPE_RESIGNATION = "RESIGNATION";
    public static final String TYPE_ORDER = "ORDER";
    public static final String TYPE_QUALITY = "QUALITY";
    private static final int STATUS_ACTIVE = 1;

    @Resource
    private ApprovalDefaultAuditorMapper approvalDefaultAuditorMapper;

    @Resource
    private UserMapper userMapper;

    public Long resolveAuditorId(String tenantCode,
                                 String approvalType,
                                 Long applyUserId,
                                 Long specifiedAuditorId,
                                 String permissionCode,
                                 boolean strictSpecified) {
        return resolveAuditorIds(tenantCode, approvalType, applyUserId, specifiedAuditorId, null, permissionCode, strictSpecified)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到可用审批人，请先配置默认审批人或审批角色权限"));
    }

    public List<Long> resolveAuditorIds(String tenantCode,
                                        String approvalType,
                                        Long applyUserId,
                                        Long specifiedAuditorId,
                                        List<Long> specifiedAuditorIds,
                                        String permissionCode,
                                        boolean strictSpecified) {
        String normalizedType = normalizeType(approvalType);
        List<Long> permissionAuditorIds = StringUtils.hasText(tenantCode) && StringUtils.hasText(permissionCode)
                ? userMapper.selectActiveApproverIdsByPermission(tenantCode, permissionCode)
                : List.of();
        List<Long> specifiedIds = normalizeAuditorIds(specifiedAuditorIds, null);
        if (!specifiedIds.isEmpty()) {
            validateAuditorIds(applyUserId, specifiedIds, permissionAuditorIds, strictSpecified);
            return specifiedIds;
        }

        Long specified = normalizeAuditorId(specifiedAuditorId);
        if (specified != null) {
            validateNotSelf(applyUserId, specified);
            if (permissionAuditorIds.contains(specified)) {
                return List.of(specified);
            }
            if (strictSpecified) {
                throw new BusinessException("所选审批人没有对应审批权限，请重新选择");
            }
        }

        List<Long> defaultAuditorIds = findActiveAuditorIds(tenantCode, normalizedType);
        if (!defaultAuditorIds.isEmpty()) {
            validateAuditorIds(applyUserId, defaultAuditorIds, permissionAuditorIds, false);
            return defaultAuditorIds;
        }

        Long fallbackAuditorId = permissionAuditorIds.stream()
                .filter(id -> id != null && id > 0)
                .filter(id -> applyUserId == null || !applyUserId.equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到可用审批人，请先配置默认审批人或审批角色权限"));
        return List.of(fallbackAuditorId);
    }

    public List<ApprovalAuditorOptionVO> applyDefaultMark(String approvalType, List<ApprovalAuditorOptionVO> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<Long> defaultAuditorIds = findActiveAuditorIds(TenantPermissionContext.getTenantCode(), normalizeType(approvalType));
        options.forEach(item -> item.setDefaultAuditor(item.getId() != null && defaultAuditorIds.contains(item.getId())));
        return options.stream()
                .sorted(Comparator.comparing((ApprovalAuditorOptionVO item) -> !Boolean.TRUE.equals(item.getDefaultAuditor()))
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .toList();
    }

    public String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LEAVE" -> TYPE_LEAVE;
            case "FINANCE" -> TYPE_FINANCE;
            case "RESIGNATION" -> TYPE_RESIGNATION;
            case "ORDER", "ORDER_SALES", "ORDER_PRODUCTION", "SALES", "PRODUCTION" -> TYPE_ORDER;
            case "QUALITY", "BADPRODUCT", "BAD_PRODUCT" -> TYPE_QUALITY;
            default -> throw new BusinessException("审批类型不合法");
        };
    }

    public String permissionCode(String approvalType) {
        return switch (normalizeType(approvalType)) {
            case TYPE_LEAVE -> PermissionCodeEnum.CODE_APPROVAL_LEAVE_AUDIT;
            case TYPE_FINANCE -> PermissionCodeEnum.CODE_APPROVAL_FINANCE_AUDIT;
            case TYPE_RESIGNATION -> PermissionCodeEnum.CODE_APPROVAL_RESIGNATION_AUDIT;
            case TYPE_ORDER -> PermissionCodeEnum.CODE_ORDER_ALL;
            case TYPE_QUALITY -> PermissionCodeEnum.CODE_BADPRODUCT_PROCESS;
            default -> throw new BusinessException("审批类型不合法");
        };
    }

    private Long findActiveAuditorId(String tenantCode, String type) {
        return findActiveAuditorIds(tenantCode, type).stream().findFirst().orElse(null);
    }

    private List<Long> findActiveAuditorIds(String tenantCode, String type) {
        return resolveEntityAuditorIds(findActive(tenantCode, type));
    }

    private ApprovalDefaultAuditor findActive(String tenantCode, String type) {
        if (!StringUtils.hasText(tenantCode)) {
            return null;
        }
        return approvalDefaultAuditorMapper.selectOne(new LambdaQueryWrapper<ApprovalDefaultAuditor>()
                .eq(ApprovalDefaultAuditor::getTenantCode, tenantCode)
                .eq(ApprovalDefaultAuditor::getApprovalType, normalizeType(type))
                .eq(ApprovalDefaultAuditor::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
    }

    private Long normalizeAuditorId(Long auditorId) {
        return auditorId == null || auditorId <= 0 ? null : auditorId;
    }

    private List<Long> normalizeAuditorIds(List<Long> auditorIds, Long fallbackAuditorId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (auditorIds != null) {
            for (Long auditorId : auditorIds) {
                Long normalized = normalizeAuditorId(auditorId);
                if (normalized != null) {
                    ids.add(normalized);
                }
            }
        }
        Long fallback = normalizeAuditorId(fallbackAuditorId);
        if (ids.isEmpty() && fallback != null) {
            ids.add(fallback);
        }
        return new ArrayList<>(ids);
    }

    private List<Long> resolveEntityAuditorIds(ApprovalDefaultAuditor entity) {
        if (entity == null) {
            return List.of();
        }
        List<Long> ids = parseAuditorIds(entity.getAuditorIds());
        if (ids.isEmpty() && entity.getAuditorId() != null && entity.getAuditorId() > 0) {
            return List.of(entity.getAuditorId());
        }
        return ids;
    }

    private List<Long> parseAuditorIds(String auditorIds) {
        if (!StringUtils.hasText(auditorIds)) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String raw : auditorIds.split(",")) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                long value = Long.parseLong(raw.trim());
                if (value > 0) {
                    ids.add(value);
                }
            } catch (NumberFormatException ignored) {
                // 历史脏数据不应影响审批人解析。
            }
        }
        return new ArrayList<>(ids);
    }

    private void validateAuditorIds(Long applyUserId,
                                    List<Long> auditorIds,
                                    List<Long> permissionAuditorIds,
                                    boolean strictSpecified) {
        for (Long auditorId : auditorIds) {
            validateNotSelf(applyUserId, auditorId);
            if (!permissionAuditorIds.contains(auditorId) && strictSpecified) {
                throw new BusinessException("所选审批人没有对应审批权限，请重新选择");
            }
            if (!permissionAuditorIds.contains(auditorId) && !strictSpecified) {
                throw new BusinessException("默认审批人没有对应审批权限，请先分配角色权限");
            }
        }
    }

    private void validateNotSelf(Long applyUserId, Long auditorId) {
        if (applyUserId != null && applyUserId.equals(auditorId)) {
            throw new BusinessException("审批人不能选择申请人本人");
        }
    }
}
