package my.hive_back.module.badproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.dto.PageResult;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.enums.QueryScopeEnum;
import my.hive_back.common.security.InternalUploadUrlValidator;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.badproduct.BadProductStatusEnum;
import my.hive_back.module.badproduct.mapper.BadProductMapper;
import my.hive_back.module.badproduct.model.dto.BadProductPageRequest;
import my.hive_back.module.badproduct.model.dto.BadProductProcessRequest;
import my.hive_back.module.badproduct.model.dto.BadProductSaveRequest;
import my.hive_back.module.badproduct.model.entity.BadProductRecord;
import my.hive_back.module.badproduct.model.vo.BadProductVO;
import my.hive_back.module.approval.service.ApprovalAuditorCandidateService;
import my.hive_back.module.approval.service.ApprovalDefaultAuditorService;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
/**
 * BadProductService 属于小程序后端坏品模块，实现核心业务编排与规则逻辑。
 */
@Service
public class BadProductService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String BUSINESS_SCOPE_AFTER_SALES = "afterSales";
    private static final String APPROVAL_TYPE_QUALITY = "QUALITY";
    private static final int MAX_PARALLEL_APPROVERS = 10;
    private static final Set<String> AFTER_SALES_TYPES = Set.of(
            "motor",
            "manual_track",
            "electric_track",
            "fabric",
            "electric_roller_blind",
            "manual_roller_blind",
            "wear_part",
            "craft",
            "installation",
            "measurement",
            "after_sales_other"
    );

    @Resource
    private BadProductMapper badProductMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

    @Resource
    private ApprovalAuditorCandidateService approvalAuditorCandidateService;

    @Resource
    private ApprovalDefaultAuditorService approvalDefaultAuditorService;

    public PageResult<BadProductVO> page(BadProductPageRequest request) {
        LambdaQueryWrapper<BadProductRecord> wrapper = new LambdaQueryWrapper<>();

        String status = normalizeQueryValue(request.getStatus());
        String type = normalizeQueryValue(request.getType());
        String businessScope = normalizeQueryValue(request.getBusinessScope());
        String dateText = normalizeQueryValue(request.getDate());

        boolean afterSalesScope = BUSINESS_SCOPE_AFTER_SALES.equalsIgnoreCase(businessScope);
        if (status != null && !QueryScopeEnum.ALL.matches(status)) {
            wrapper.eq(BadProductRecord::getStatus, status);
        }
        if (type != null && !QueryScopeEnum.ALL.matches(type)) {
            if (afterSalesScope != AFTER_SALES_TYPES.contains(type)) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.eq(BadProductRecord::getType, type);
            }
        } else if (afterSalesScope) {
            wrapper.in(BadProductRecord::getType, AFTER_SALES_TYPES);
        } else {
            wrapper.notIn(BadProductRecord::getType, AFTER_SALES_TYPES);
        }
        if (dateText != null) {
            LocalDate date = LocalDate.parse(dateText, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            wrapper.ge(BadProductRecord::getCreateTime, date.atStartOfDay())
                    .lt(BadProductRecord::getCreateTime, date.plusDays(1).atStartOfDay());
        }

        wrapper.orderByDesc(BadProductRecord::getCreateTime);
        Page<BadProductRecord> page = badProductMapper.selectPage(
                new Page<>(safePageNum(request.getPageNum()), safePageSize(request.getPageSize())),
                wrapper
        );

        List<BadProductVO> records = page.getRecords().stream().map(this::toVO).toList();
        PageResult<BadProductVO> result = new PageResult<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setData(records);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(BadProductSaveRequest request) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();
        User user = userMapper.selectById(userId);

        BadProductRecord entity;
        if (request.getDefectiveId() != null && !request.getDefectiveId().isBlank()) {
            entity = badProductMapper.selectOne(new LambdaQueryWrapper<BadProductRecord>()
                    .eq(BadProductRecord::getDefectiveId, request.getDefectiveId()));
            if (entity == null) {
                throw new BusinessException("质量记录不存在");
            }
        } else {
            entity = new BadProductRecord();
            entity.setTenantCode(tenantCode);
            entity.setDefectiveId(codeGeneratorUtil.generateCode("DC", 4));
            entity.setCreatorId(userId);
            entity.setCreatorName(user == null ? "未知用户" : user.getName());
            entity.setStatus(BadProductStatusEnum.PENDING.getCode());
            entity.setCreateTime(LocalDateTime.now());
        }

        entity.setOrderId(blankToNull(request.getOrderId()));
        entity.setType(request.getType());
        entity.setQuantity(request.getQuantity());
        entity.setLossAmount(request.getLossAmount());
        entity.setDescription(blankToNull(request.getDescription()));
        if (entity.getId() == null || BadProductStatusEnum.PENDING.getCode().equals(entity.getStatus())) {
            entity.setResponsiblePerson(null);
            entity.setProcessMeasure(null);
            entity.setImprovementPlan(null);
        }
        entity.setAttachmentName(blankToNull(request.getAttachmentName()));
        entity.setAttachmentUrl(InternalUploadUrlValidator.normalizeStoredUploadUrl(
                request.getAttachmentUrl(),
                tenantCode,
                "bad-product"
        ));
        entity.setAttachmentSize(safeAttachmentSize(request.getAttachmentSize()));

        if (entity.getId() == null) {
            badProductMapper.insert(entity);
        } else {
            badProductMapper.updateById(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void process(BadProductProcessRequest request) {
        BadProductRecord entity = badProductMapper.selectOne(new LambdaQueryWrapper<BadProductRecord>()
                .eq(BadProductRecord::getDefectiveId, request.getDefectiveId()));
        if (entity == null) {
            throw new BusinessException("质量记录不存在");
        }
        if (BadProductStatusEnum.PROCESSED.getCode().equals(entity.getStatus())) {
            throw new BusinessException("质量记录已处理，请勿重复提交");
        }
        if (hasPendingQualityApproval(entity.getDefectiveId())) {
            throw new BusinessException("该质量记录已提交审核，请审核完成后再操作");
        }
        entity.setStatus(BadProductStatusEnum.PENDING_AUDIT.getCode());
        entity.setProcessMethod(request.getMethod());
        entity.setProcessRemark(blankToNull(request.getRemark()));
        entity.setResponsiblePerson(blankToNull(request.getResponsiblePerson()));
        entity.setProcessMeasure(blankToNull(request.getProcessMeasure()));
        entity.setImprovementPlan(blankToNull(request.getImprovementPlan()));
        badProductMapper.updateById(entity);

        List<Long> auditorIds = approvalDefaultAuditorService.resolveAuditorIds(
                entity.getTenantCode(),
                APPROVAL_TYPE_QUALITY,
                TenantPermissionContext.getUserId(),
                null,
                null,
                PermissionCodeEnum.CODE_BADPRODUCT_PROCESS,
                false);
        List<Long> normalizedAuditorIds = normalizeApprovalAuditorIds(auditorIds);
        approvalAuditorCandidateService.replaceActiveCandidates(
                entity.getTenantCode(),
                APPROVAL_TYPE_QUALITY,
                qualityApprovalCode(entity.getDefectiveId()),
                normalizedAuditorIds);
        notifyQualityPendingApprovers(entity, normalizedAuditorIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveProcess(String defectiveId) {
        BadProductRecord entity = findByDefectiveId(defectiveId);
        if (!BadProductStatusEnum.PENDING_AUDIT.getCode().equals(entity.getStatus())) {
            throw new BusinessException("当前质量记录不在审核中");
        }
        entity.setStatus(BadProductStatusEnum.PROCESSED.getCode());
        badProductMapper.updateById(entity);
        notifyBadProductProcessed(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectProcessApproval(String defectiveId) {
        BadProductRecord entity = findByDefectiveId(defectiveId);
        if (!BadProductStatusEnum.PENDING_AUDIT.getCode().equals(entity.getStatus())) {
            throw new BusinessException("当前质量记录不在审核中");
        }
        entity.setStatus(BadProductStatusEnum.PENDING.getCode());
        badProductMapper.updateById(entity);
    }

    public boolean hasPendingQualityApproval(String defectiveId) {
        if (defectiveId == null || defectiveId.isBlank()) {
            return false;
        }
        BadProductRecord entity = badProductMapper.selectOne(new LambdaQueryWrapper<BadProductRecord>()
                .eq(BadProductRecord::getDefectiveId, defectiveId.trim())
                .last("LIMIT 1"));
        if (entity == null || !BadProductStatusEnum.PENDING_AUDIT.getCode().equals(entity.getStatus())) {
            return false;
        }
        return !approvalAuditorCandidateService.findPendingAuditorIds(
                entity.getTenantCode(),
                APPROVAL_TYPE_QUALITY,
                qualityApprovalCode(entity.getDefectiveId())).isEmpty();
    }

    public String qualityApprovalCode(String defectiveId) {
        if (defectiveId == null || defectiveId.isBlank()) {
            throw new BusinessException("质量编号不能为空");
        }
        return defectiveId.trim();
    }

    public String qualityApprovalType() {
        return APPROVAL_TYPE_QUALITY;
    }

    private BadProductRecord findByDefectiveId(String defectiveId) {
        BadProductRecord entity = badProductMapper.selectOne(new LambdaQueryWrapper<BadProductRecord>()
                .eq(BadProductRecord::getDefectiveId, defectiveId));
        if (entity == null) {
            throw new BusinessException("质量记录不存在");
        }
        return entity;
    }

    private void notifyBadProductProcessed(BadProductRecord entity) {
        Long currentUserId = TenantPermissionContext.getUserId();
        if (entity.getCreatorId() == null || entity.getCreatorId().equals(currentUserId)) {
            return;
        }
        wechatSubscribeNotificationService.sendTodoAfterCommit(
                entity.getCreatorId(),
                currentOperatorName(),
                "质量处理结果",
                "质量记录 " + entity.getDefectiveId() + " 已处理",
                "/pages/badProduct/badProduct"
        );
    }

    private void notifyQualityPendingApprovers(BadProductRecord entity, List<Long> auditorIds) {
        if (auditorIds == null || auditorIds.isEmpty()) {
            return;
        }
        String operatorName = currentOperatorName();
        for (Long auditorId : auditorIds) {
            wechatSubscribeNotificationService.sendTodoAfterCommit(
                    auditorId,
                    operatorName,
                    "质量审核待处理",
                    "质量记录 " + entity.getDefectiveId() + " 已提交处理审核",
                    "/pages/approval/approval?tab=quality"
            );
        }
    }

    private String currentOperatorName() {
        Long userId = TenantPermissionContext.getUserId();
        if (userId == null) {
            return "系统提醒";
        }
        User user = userMapper.selectById(userId);
        return user == null || user.getName() == null || user.getName().isBlank() ? "系统提醒" : user.getName();
    }

    private BadProductVO toVO(BadProductRecord entity) {
        BadProductVO vo = new BadProductVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCreator(entity.getCreatorName());
        return vo;
    }

    private List<Long> normalizeApprovalAuditorIds(List<Long> auditorIds) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        if (auditorIds != null) {
            for (Long auditorId : auditorIds) {
                if (auditorId != null && auditorId > 0) {
                    uniqueIds.add(auditorId);
                }
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new BusinessException("未找到可用审核人，请先配置质量审核人");
        }
        if (uniqueIds.size() > MAX_PARALLEL_APPROVERS) {
            throw new BusinessException("质量审核人不能超过" + MAX_PARALLEL_APPROVERS + "人");
        }
        return new ArrayList<>(uniqueIds);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeQueryValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "undefined".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private Long safeAttachmentSize(Long attachmentSize) {
        return attachmentSize == null || attachmentSize <= 0 ? null : attachmentSize;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
