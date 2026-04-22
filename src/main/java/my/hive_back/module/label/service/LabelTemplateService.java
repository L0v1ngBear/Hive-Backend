package my.hive_back.module.label.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.label.mapper.LabelTemplateMapper;
import my.hive_back.module.label.model.dto.LabelTemplateSaveRequest;
import my.hive_back.module.label.model.entity.LabelTemplate;
import my.hive_back.module.label.model.vo.LabelTemplateVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * LabelTemplateService 属于小程序后端标签模块，实现核心业务编排与规则逻辑。
 */
@Service
public class LabelTemplateService {

    private static final long MAX_FILE_SIZE = 1024 * 1024;
    private static final Pattern DOLLAR_VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern BRACE_VARIABLE_PATTERN = Pattern.compile("(?<!\\$)\\{([^}]+)}");
    private static final String DEFAULT_LABEL_TEMPLATE = "SIZE 70 mm,50 mm\r\n"
            + "GAP 2 mm,0 mm\r\n"
            + "DIRECTION 1\r\n"
            + "CLS\r\n"
            + "TEXT 30,30,\"TSS24.BF2\",0,1,1,\"??: ${modelCode}\"\r\n"
            + "TEXT 30,70,\"TSS24.BF2\",0,1,1,\"??: ${meters} m\"\r\n"
            + "TEXT 30,110,\"TSS24.BF2\",0,1,1,\"??: ${spec}\"\r\n"
            + "BARCODE 30,160,\"128\",80,1,0,2,2,\"${barcode}\"\r\n"
            + "TEXT 30,250,\"TSS24.BF2\",0,1,1,\"${barcode}\"\r\n"
            + "PRINT 1,1";
    @Resource
    private LabelTemplateMapper labelTemplateMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<LabelTemplateVO> list(String printType) {
        LambdaQueryWrapper<LabelTemplate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LabelTemplate::getStatus, 1);
        if (StringUtils.isNotBlank(printType)) {
            queryWrapper.eq(LabelTemplate::getPrintType, printType);
        }
        queryWrapper.orderByDesc(LabelTemplate::getIsDefault);
        queryWrapper.orderByDesc(LabelTemplate::getUpdateTime);
        List<LabelTemplate> templates = labelTemplateMapper.selectList(queryWrapper);
        if (templates.isEmpty() && (!StringUtils.isNotBlank(printType) || "label".equals(printType))) {
            templates = List.of(createDefaultLabelTemplate());
        }
        return templates.stream().map(this::toVO).toList();
    }

    public LabelTemplateVO detail(Long id) {
        LabelTemplate template = labelTemplateMapper.selectOne(new LambdaQueryWrapper<LabelTemplate>()
                .eq(LabelTemplate::getId, id));
        if (template == null) {
            throw new BusinessException("标签模板不存在");
        }
        return toVO(template);
    }

    public LabelTemplateVO defaultTemplate(String printType) {
        List<LabelTemplateVO> templates = list(StringUtils.isNotBlank(printType) ? printType : "label");
        if (templates.isEmpty()) {
            throw new BusinessException("暂无可用标签模板，请先在管理端上传");
        }
        return templates.stream().filter(item -> Integer.valueOf(1).equals(item.getIsDefault())).findFirst().orElse(templates.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public LabelTemplateVO save(LabelTemplateSaveRequest request) {
        LabelTemplate template = new LabelTemplate();
        template.setTenantCode(TenantPermissionContext.getTenantCode());
        template.setName(request.getName().trim());
        template.setPrintType(resolvePrintType(request.getPrintType()));
        template.setContent(request.getContent());
        template.setVariables(String.join(",", extractVariables(request.getContent())));
        template.setIsDefault(Integer.valueOf(1).equals(request.getIsDefault()) ? 1 : 0);
        template.setStatus(1);
        template.setIsDeleted(0);
        template.setCreatorId(TenantPermissionContext.getUserId());
        labelTemplateMapper.insert(template);

        if (Integer.valueOf(1).equals(template.getIsDefault())) {
            clearOtherDefault(template);
        }
        return toVO(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public LabelTemplateVO upload(MultipartFile file, String name, String printType, Integer isDefault) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 PRN 模板文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("模板文件不能超过 1MB");
        }

        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".prn") && !lowerName.endsWith(".txt")) {
            throw new BusinessException("仅支持上传 .prn 或 .txt 模板文件");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("模板文件读取失败");
        }
        if (!StringUtils.isNotBlank(content)) {
            throw new BusinessException("模板文件内容不能为空");
        }

        LabelTemplateSaveRequest request = new LabelTemplateSaveRequest();
        request.setName(StringUtils.isNotBlank(name) ? name : originalFilename);
        request.setPrintType(printType);
        request.setContent(content);
        request.setIsDefault(isDefault);
        LabelTemplateVO vo = save(request);

        LabelTemplate update = new LabelTemplate();
        update.setId(vo.getId());
        update.setFileName(originalFilename);
        update.setFileSize(file.getSize());
        labelTemplateMapper.updateById(update);
        return detail(vo.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        LabelTemplate template = labelTemplateMapper.selectOne(new LambdaQueryWrapper<LabelTemplate>()
                .eq(LabelTemplate::getId, id));
        if (template == null) {
            throw new BusinessException("标签模板不存在");
        }
        template.setIsDefault(1);
        labelTemplateMapper.updateById(template);
        clearOtherDefault(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        LambdaUpdateWrapper<LabelTemplate> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LabelTemplate::getId, id)
                .set(LabelTemplate::getStatus, 0);
        labelTemplateMapper.update(null, updateWrapper);
    }

    private LabelTemplate createDefaultLabelTemplate() {
        LabelTemplate template = new LabelTemplate();
        template.setTenantCode(TenantPermissionContext.getTenantCode());
        template.setName("系统默认面料标签");
        template.setPrintType("label");
        template.setContent(DEFAULT_LABEL_TEMPLATE);
        template.setVariables(String.join(",", extractVariables(DEFAULT_LABEL_TEMPLATE)));
        template.setFileName("system-default.prn");
        template.setFileSize((long) DEFAULT_LABEL_TEMPLATE.getBytes(StandardCharsets.UTF_8).length);
        template.setIsDefault(1);
        template.setStatus(1);
        template.setIsDeleted(0);
        template.setCreatorId(TenantPermissionContext.getUserId());
        labelTemplateMapper.insert(template);
        return template;
    }

    private void clearOtherDefault(LabelTemplate template) {
        LambdaUpdateWrapper<LabelTemplate> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LabelTemplate::getPrintType, template.getPrintType())
                .ne(LabelTemplate::getId, template.getId())
                .set(LabelTemplate::getIsDefault, 0);
        labelTemplateMapper.update(null, updateWrapper);
    }

    private LabelTemplateVO toVO(LabelTemplate template) {
        LabelTemplateVO vo = new LabelTemplateVO();
        BeanUtils.copyProperties(template, vo);
        if (StringUtils.isNotBlank(template.getVariables())) {
            vo.setVariables(Arrays.stream(template.getVariables().split(",")).filter(StringUtils::isNotBlank).toList());
        } else {
            vo.setVariables(Collections.emptyList());
        }
        return vo;
    }

    private String resolvePrintType(String printType) {
        return StringUtils.isNotBlank(printType) ? printType : "label";
    }

    private List<String> extractVariables(String content) {
        Set<String> variables = new LinkedHashSet<>();
        collectVariables(DOLLAR_VARIABLE_PATTERN.matcher(content), variables);
        collectVariables(BRACE_VARIABLE_PATTERN.matcher(content), variables);
        return variables.stream().toList();
    }

    private void collectVariables(Matcher matcher, Set<String> variables) {
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (StringUtils.isNotBlank(variable)) {
                variables.add(variable.trim());
            }
        }
    }
}
