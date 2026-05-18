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
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import my.hive_back.module.wechat.service.WechatSubscribeNotificationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final Set<String> AFTER_SALES_TYPES = Set.of(
            "after_sales",
            "return_exchange",
            "compensation",
            "customer_complaint"
    );

    @Resource
    private BadProductMapper badProductMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WechatSubscribeNotificationService wechatSubscribeNotificationService;

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
        entity.setResponsiblePerson(blankToNull(request.getResponsiblePerson()));
        entity.setProcessMeasure(blankToNull(request.getProcessMeasure()));
        entity.setImprovementPlan(blankToNull(request.getImprovementPlan()));
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
        entity.setStatus(BadProductStatusEnum.PROCESSED.getCode());
        entity.setProcessMethod(request.getMethod());
        entity.setProcessRemark(blankToNull(request.getRemark()));
        badProductMapper.updateById(entity);
        notifyBadProductProcessed(entity);
    }

    private void notifyBadProductProcessed(BadProductRecord entity) {
        Long currentUserId = TenantPermissionContext.getUserId();
        if (entity.getCreatorId() == null || entity.getCreatorId().equals(currentUserId)) {
            return;
        }
        wechatSubscribeNotificationService.sendTodoAfterCommit(
                entity.getCreatorId(),
                "质量处理结果",
                "质量记录 " + entity.getDefectiveId() + " 已处理",
                "/pages/badProduct/badProduct"
        );
    }

    private BadProductVO toVO(BadProductRecord entity) {
        BadProductVO vo = new BadProductVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCreator(entity.getCreatorName());
        return vo;
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
