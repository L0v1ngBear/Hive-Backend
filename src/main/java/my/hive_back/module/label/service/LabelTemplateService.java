package my.hive_back.module.label.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.enums.BinaryFlagEnum;
import my.hive_back.common.enums.CommonStatusEnum;
import my.hive_back.common.enums.DeleteFlagEnum;
import my.hive_back.module.label.LabelPrintTypeEnum;
import my.hive_back.module.label.mapper.LabelTemplateMapper;
import my.hive_back.module.label.model.dto.LabelTemplateSaveRequest;
import my.hive_back.module.label.model.entity.LabelTemplate;
import my.hive_back.module.label.model.vo.LabelTemplateVO;
import my.hive_back.module.label.model.vo.LabelTemplateVariableVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final BigDecimal DEFAULT_WIDTH_MM = new BigDecimal("70");
    private static final BigDecimal DEFAULT_HEIGHT_MM = new BigDecimal("50");
    private static final List<LabelTemplateVariableVO> LABEL_VARIABLES = List.of(
            new LabelTemplateVariableVO("条码", "barcode", "barcode", "CL20260421001"),
            new LabelTemplateVariableVO("布匹二维码", "labelQrPayload", "qrcode", "{\"version\":\"1\",\"codeType\":\"inventory_barcode\",\"barcode\":\"CL20260421001\"}"),
            new LabelTemplateVariableVO("型号", "modelCode", "text", "M-2026-A"),
            new LabelTemplateVariableVO("米数", "meters", "text", "120.50"),
            new LabelTemplateVariableVO("规格", "spec", "text", "160"),
            new LabelTemplateVariableVO("批次", "batchNo", "text", "BATCH-001"),
            new LabelTemplateVariableVO("入库时间", "inboundTime", "text", "2026-04-21"),
            new LabelTemplateVariableVO("客户", "customerName", "text", "客户名称")
    );
    private static final List<LabelTemplateVariableVO> ORDER_FLOW_VARIABLES = List.of(
            new LabelTemplateVariableVO("流转条码", "flowBarcode", "barcode", "HIVE_ORDER_FLOW:sales:FLOW202605190001:SO202605190001"),
            new LabelTemplateVariableVO("流转二维码", "flowQrPayload", "qrcode", "{\"version\":\"1\",\"codeType\":\"order_flow\",\"orderId\":\"SO202605190001\",\"orderType\":\"sales\",\"flowScanCode\":\"HIVE_ORDER_FLOW:sales:FLOW202605190001:SO202605190001\"}"),
            new LabelTemplateVariableVO("订单编号", "orderId", "text", "SO202605190001"),
            new LabelTemplateVariableVO("订单类型", "orderTypeLabel", "text", "销售订单"),
            new LabelTemplateVariableVO("当前状态", "currentStatusText", "text", "待确认"),
            new LabelTemplateVariableVO("订单小项", "orderCategoryLabel", "text", "大货"),
            new LabelTemplateVariableVO("客户名称", "customerName", "text", "上海某服饰"),
            new LabelTemplateVariableVO("项目名称", "projectName", "text", "春季面料项目"),
            new LabelTemplateVariableVO("品牌", "brandName", "text", "客户品牌")
    );
    private static final List<LabelTemplateVariableVO> EQUIPMENT_INSPECTION_VARIABLES = List.of(
            new LabelTemplateVariableVO("设备编码", "equipmentCode", "barcode", "EQ202605190001"),
            new LabelTemplateVariableVO("固定巡检码", "inspectionQrPayload", "qrcode", "HIVE_EQUIPMENT:EQ202605190001"),
            new LabelTemplateVariableVO("设备名称", "equipmentName", "text", "定型机01"),
            new LabelTemplateVariableVO("设备类型", "equipmentType", "text", "生产设备"),
            new LabelTemplateVariableVO("设备位置", "location", "text", "一车间"),
            new LabelTemplateVariableVO("负责人", "responsiblePerson", "text", "设备管理员"),
            new LabelTemplateVariableVO("巡检周期", "inspectionCycleDays", "text", "7")
    );
    private static final Map<String, List<LabelTemplateVariableVO>> VARIABLE_MAP = Map.of(
            LabelPrintTypeEnum.LABEL.getCode(), LABEL_VARIABLES,
            "order_flow", ORDER_FLOW_VARIABLES,
            "equipment_inspection", EQUIPMENT_INSPECTION_VARIABLES
    );
    private static final String DEFAULT_LABEL_TEMPLATE = "SIZE 70 mm,50 mm\r\n"
            + "GAP 2 mm,0 mm\r\n"
            + "DIRECTION 1\r\n"
            + "CLS\r\n"
            + "TEXT 30,30,\"TSS24.BF2\",0,1,1,\"型号: ${modelCode}\"\r\n"
            + "TEXT 30,70,\"TSS24.BF2\",0,1,1,\"米数: ${meters} m\"\r\n"
            + "TEXT 30,110,\"TSS24.BF2\",0,1,1,\"规格: ${spec}\"\r\n"
            + "BARCODE 30,160,\"128\",80,1,0,2,2,\"${barcode}\"\r\n"
            + "TEXT 30,250,\"TSS24.BF2\",0,1,1,\"${barcode}\"\r\n"
            + "PRINT 1,1";
    private static final String DEFAULT_ORDER_FLOW_TEMPLATE = "SIZE 60 mm,40 mm\r\n"
            + "GAP 2 mm,0 mm\r\n"
            + "DIRECTION 1\r\n"
            + "CLS\r\n"
            + "TEXT 24,18,\"TSS24.BF2\",0,1,1,\"订单流转码\"\r\n"
            + "TEXT 24,52,\"TSS24.BF2\",0,1,1,\"${orderTypeLabel}  ${currentStatusText}\"\r\n"
            + "BARCODE 24,88,\"128\",54,1,0,2,2,\"${flowBarcode}\"\r\n"
            + "QRCODE 330,36,L,5,A,0,M2,S7,\"${flowQrPayload}\"\r\n"
            + "TEXT 24,162,\"TSS24.BF2\",0,1,1,\"${customerName}\"\r\n"
            + "TEXT 24,198,\"TSS24.BF2\",0,1,1,\"${orderCategoryLabel} / ${brandName}\"\r\n"
            + "TEXT 24,234,\"TSS24.BF2\",0,1,1,\"${orderId}\"\r\n"
            + "PRINT 1,1";
    private static final String DEFAULT_EQUIPMENT_INSPECTION_TEMPLATE = "SIZE 60 mm,40 mm\r\n"
            + "GAP 2 mm,0 mm\r\n"
            + "DIRECTION 1\r\n"
            + "CLS\r\n"
            + "TEXT 24,18,\"TSS24.BF2\",0,1,1,\"设备巡检码\"\r\n"
            + "TEXT 24,52,\"TSS24.BF2\",0,1,1,\"${equipmentName}\"\r\n"
            + "QRCODE 320,30,L,5,A,0,M2,S7,\"${inspectionQrPayload}\"\r\n"
            + "BARCODE 24,92,\"128\",56,1,0,2,2,\"${equipmentCode}\"\r\n"
            + "TEXT 24,170,\"TSS24.BF2\",0,1,1,\"位置: ${location}\"\r\n"
            + "TEXT 24,206,\"TSS24.BF2\",0,1,1,\"负责人: ${responsiblePerson}\"\r\n"
            + "TEXT 24,242,\"TSS24.BF2\",0,1,1,\"${equipmentCode}\"\r\n"
            + "PRINT 1,1";
    @Resource
    private LabelTemplateMapper labelTemplateMapper;

    public List<LabelTemplateVariableVO> variables(String printType) {
        return VARIABLE_MAP.getOrDefault(resolvePrintType(printType), LABEL_VARIABLES);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<LabelTemplateVO> list(String printType) {
        LambdaQueryWrapper<LabelTemplate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LabelTemplate::getStatus, CommonStatusEnum.ENABLED.getCode());
        if (StringUtils.isNotBlank(printType)) {
            queryWrapper.eq(LabelTemplate::getPrintType, printType);
        }
        queryWrapper.orderByDesc(LabelTemplate::getIsDefault);
        queryWrapper.orderByDesc(LabelTemplate::getUpdateTime);
        List<LabelTemplate> templates = labelTemplateMapper.selectList(queryWrapper);
        if (templates.isEmpty()) {
            String resolvedPrintType = resolvePrintType(printType);
            if (LabelPrintTypeEnum.LABEL.getCode().equals(resolvedPrintType)) {
                templates = List.of(createDefaultLabelTemplate());
            } else if ("order_flow".equals(resolvedPrintType)) {
                templates = List.of(createDefaultOrderFlowTemplate());
            } else if ("equipment_inspection".equals(resolvedPrintType)) {
                templates = List.of(createDefaultEquipmentInspectionTemplate());
            }
        }
        templates.forEach(this::repairLegacySystemLabelTemplateIfNecessary);
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
        List<LabelTemplateVO> templates = list(StringUtils.isNotBlank(printType) ? printType : LabelPrintTypeEnum.LABEL.getCode());
        if (templates.isEmpty()) {
            throw new BusinessException("暂无可用标签模板，请先在管理端上传");
        }
        return templates.stream().filter(item -> BinaryFlagEnum.YES.matches(item.getIsDefault())).findFirst().orElse(templates.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public LabelTemplateVO save(LabelTemplateSaveRequest request) {
        LabelTemplate template = resolveTemplateForSave(request.getId());
        template.setName(request.getName().trim());
        template.setPrintType(resolvePrintType(request.getPrintType()));
        template.setContent(request.getContent());
        template.setDesignJson(request.getDesignJson());
        template.setWidthMm(request.getWidthMm() == null ? DEFAULT_WIDTH_MM : request.getWidthMm());
        template.setHeightMm(request.getHeightMm() == null ? DEFAULT_HEIGHT_MM : request.getHeightMm());
        template.setVariables(String.join(",", extractVariables(request.getContent())));
        template.setIsDefault(BinaryFlagEnum.YES.matches(request.getIsDefault()) ? BinaryFlagEnum.YES.getCode() : BinaryFlagEnum.NO.getCode());
        template.setStatus(CommonStatusEnum.ENABLED.getCode());
        template.setIsDeleted(DeleteFlagEnum.NORMAL.getCode());
        if (template.getId() == null) {
            template.setTenantCode(TenantPermissionContext.getTenantCode());
            template.setCreatorId(TenantPermissionContext.getUserId());
            labelTemplateMapper.insert(template);
        } else {
            labelTemplateMapper.updateById(template);
        }

        if (BinaryFlagEnum.YES.matches(template.getIsDefault())) {
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
        request.setWidthMm(DEFAULT_WIDTH_MM);
        request.setHeightMm(DEFAULT_HEIGHT_MM);
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
        template.setIsDefault(BinaryFlagEnum.YES.getCode());
        labelTemplateMapper.updateById(template);
        clearOtherDefault(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        LambdaUpdateWrapper<LabelTemplate> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LabelTemplate::getId, id)
                .set(LabelTemplate::getStatus, CommonStatusEnum.DISABLED.getCode());
        labelTemplateMapper.update(null, updateWrapper);
    }

    private LabelTemplate resolveTemplateForSave(Long id) {
        if (id == null) {
            return new LabelTemplate();
        }
        LabelTemplate template = labelTemplateMapper.selectOne(new LambdaQueryWrapper<LabelTemplate>()
                .eq(LabelTemplate::getId, id));
        if (template == null) {
            throw new BusinessException("标签模板不存在");
        }
        return template;
    }

    private LabelTemplate createDefaultLabelTemplate() {
        LabelTemplate template = new LabelTemplate();
        template.setTenantCode(TenantPermissionContext.getTenantCode());
        template.setName("系统默认面料标签");
        template.setPrintType(LabelPrintTypeEnum.LABEL.getCode());
        template.setContent(DEFAULT_LABEL_TEMPLATE);
        template.setWidthMm(DEFAULT_WIDTH_MM);
        template.setHeightMm(DEFAULT_HEIGHT_MM);
        template.setVariables(String.join(",", extractVariables(DEFAULT_LABEL_TEMPLATE)));
        template.setFileName("system-default.prn");
        template.setFileSize((long) DEFAULT_LABEL_TEMPLATE.getBytes(StandardCharsets.UTF_8).length);
        template.setIsDefault(BinaryFlagEnum.YES.getCode());
        template.setStatus(CommonStatusEnum.ENABLED.getCode());
        template.setIsDeleted(DeleteFlagEnum.NORMAL.getCode());
        template.setCreatorId(TenantPermissionContext.getUserId());
        labelTemplateMapper.insert(template);
        return template;
    }

    private LabelTemplate createDefaultOrderFlowTemplate() {
        LabelTemplate template = new LabelTemplate();
        template.setTenantCode(TenantPermissionContext.getTenantCode());
        template.setName("系统默认订单流转码");
        template.setPrintType("order_flow");
        template.setContent(DEFAULT_ORDER_FLOW_TEMPLATE);
        template.setWidthMm(new BigDecimal("60"));
        template.setHeightMm(new BigDecimal("40"));
        template.setVariables(String.join(",", extractVariables(DEFAULT_ORDER_FLOW_TEMPLATE)));
        template.setFileName("system-default-order-flow.prn");
        template.setFileSize((long) DEFAULT_ORDER_FLOW_TEMPLATE.getBytes(StandardCharsets.UTF_8).length);
        template.setIsDefault(BinaryFlagEnum.YES.getCode());
        template.setStatus(CommonStatusEnum.ENABLED.getCode());
        template.setIsDeleted(DeleteFlagEnum.NORMAL.getCode());
        template.setCreatorId(TenantPermissionContext.getUserId());
        labelTemplateMapper.insert(template);
        return template;
    }

    private LabelTemplate createDefaultEquipmentInspectionTemplate() {
        LabelTemplate template = new LabelTemplate();
        template.setTenantCode(TenantPermissionContext.getTenantCode());
        template.setName("系统默认设备巡检码");
        template.setPrintType("equipment_inspection");
        template.setContent(DEFAULT_EQUIPMENT_INSPECTION_TEMPLATE);
        template.setWidthMm(new BigDecimal("60"));
        template.setHeightMm(new BigDecimal("40"));
        template.setVariables(String.join(",", extractVariables(DEFAULT_EQUIPMENT_INSPECTION_TEMPLATE)));
        template.setFileName("system-default-equipment-inspection.prn");
        template.setFileSize((long) DEFAULT_EQUIPMENT_INSPECTION_TEMPLATE.getBytes(StandardCharsets.UTF_8).length);
        template.setIsDefault(BinaryFlagEnum.YES.getCode());
        template.setStatus(CommonStatusEnum.ENABLED.getCode());
        template.setIsDeleted(DeleteFlagEnum.NORMAL.getCode());
        template.setCreatorId(TenantPermissionContext.getUserId());
        labelTemplateMapper.insert(template);
        return template;
    }

    private void repairLegacySystemLabelTemplateIfNecessary(LabelTemplate template) {
        if (template == null || !LabelPrintTypeEnum.LABEL.getCode().equals(template.getPrintType())) {
            return;
        }
        if (!isLegacySystemLabelTemplate(template)) {
            return;
        }
        template.setName("系统默认面料标签");
        template.setContent(DEFAULT_LABEL_TEMPLATE);
        template.setWidthMm(DEFAULT_WIDTH_MM);
        template.setHeightMm(DEFAULT_HEIGHT_MM);
        template.setVariables(String.join(",", extractVariables(DEFAULT_LABEL_TEMPLATE)));
        template.setFileName("system-default.prn");
        template.setFileSize((long) DEFAULT_LABEL_TEMPLATE.getBytes(StandardCharsets.UTF_8).length);
        labelTemplateMapper.updateById(template);
    }

    private boolean isLegacySystemLabelTemplate(LabelTemplate template) {
        String fileName = template.getFileName() == null ? "" : template.getFileName().trim();
        String content = template.getContent() == null ? "" : template.getContent();
        boolean systemFile = "default-label.prn".equalsIgnoreCase(fileName) || "system-default.prn".equalsIgnoreCase(fileName);
        boolean legacyContent = content.contains("生产厂家：XX有限责任公司")
                || content.contains("??: ${")
                || content.startsWith("^XA");
        return systemFile && legacyContent;
    }

    private void clearOtherDefault(LabelTemplate template) {
        LambdaUpdateWrapper<LabelTemplate> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LabelTemplate::getPrintType, template.getPrintType())
                .ne(LabelTemplate::getId, template.getId())
                .set(LabelTemplate::getIsDefault, BinaryFlagEnum.NO.getCode());
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
        return StringUtils.isNotBlank(printType) ? printType : LabelPrintTypeEnum.LABEL.getCode();
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
