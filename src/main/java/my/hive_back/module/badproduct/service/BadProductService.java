package my.hive_back.module.badproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.dto.PageResult;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.module.badproduct.mapper.BadProductMapper;
import my.hive_back.module.badproduct.model.dto.BadProductPageRequest;
import my.hive_back.module.badproduct.model.dto.BadProductProcessRequest;
import my.hive_back.module.badproduct.model.dto.BadProductSaveRequest;
import my.hive_back.module.badproduct.model.entity.BadProductRecord;
import my.hive_back.module.badproduct.model.vo.BadProductVO;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * BadProductService 属于小程序后端坏品模块，实现核心业务编排与规则逻辑。
 */
@Service
public class BadProductService {

    @Resource
    private BadProductMapper badProductMapper;

    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;

    @Resource
    private UserMapper userMapper;

    public PageResult<BadProductVO> page(BadProductPageRequest request) {
        LambdaQueryWrapper<BadProductRecord> wrapper = new LambdaQueryWrapper<>();

        if (request.getStatus() != null && !request.getStatus().isBlank() && !"all".equals(request.getStatus())) {
            wrapper.eq(BadProductRecord::getStatus, request.getStatus());
        }
        if (request.getType() != null && !request.getType().isBlank() && !"all".equals(request.getType())) {
            wrapper.eq(BadProductRecord::getType, request.getType());
        }
        if (request.getDate() != null && !request.getDate().isBlank()) {
            LocalDate date = LocalDate.parse(request.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            wrapper.ge(BadProductRecord::getCreateTime, date.atStartOfDay())
                    .lt(BadProductRecord::getCreateTime, date.plusDays(1).atStartOfDay());
        }

        wrapper.orderByDesc(BadProductRecord::getCreateTime);
        Page<BadProductRecord> page = badProductMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

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
                throw new BusinessException("次品记录不存在");
            }
        } else {
            entity = new BadProductRecord();
            entity.setTenantCode(tenantCode);
            entity.setDefectiveId(codeGeneratorUtil.generateCode("DC", 4));
            entity.setCreatorId(userId);
            entity.setCreatorName(user == null ? "未知用户" : user.getName());
            entity.setStatus("pending");
            entity.setCreateTime(LocalDateTime.now());
        }

        entity.setOrderId(blankToNull(request.getOrderId()));
        entity.setType(request.getType());
        entity.setQuantity(request.getQuantity());
        entity.setLossAmount(request.getLossAmount());
        entity.setDescription(blankToNull(request.getDescription()));

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
            throw new BusinessException("次品记录不存在");
        }
        entity.setStatus("processed");
        entity.setProcessMethod(request.getMethod());
        entity.setProcessRemark(blankToNull(request.getRemark()));
        badProductMapper.updateById(entity);
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
}
