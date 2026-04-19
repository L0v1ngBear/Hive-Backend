package my.hive_back.api.label;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.module.label.model.dto.LabelTemplateSaveRequest;
import my.hive_back.module.label.model.vo.LabelTemplateVO;
import my.hive_back.module.label.service.LabelTemplateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
/**
 * LabelTemplateController 是小程序后端标签入口控制类，负责接收请求并调用对应服务。
 */
@RestController
@RequestMapping("/label-template")
@Validated
public class LabelTemplateController {

    @Resource
    private LabelTemplateService labelTemplateService;

    @GetMapping("/list")
    @RequirePermission(value = "label:template:list", message = "您没有权限查看标签模板")
    public Result<List<LabelTemplateVO>> list(@RequestParam(required = false) String printType) {
        return Result.success(labelTemplateService.list(printType));
    }

    @GetMapping("/{id}")
    @RequirePermission(value = "label:template:detail", message = "您没有权限查看标签模板详情")
    public Result<LabelTemplateVO> detail(@PathVariable Long id) {
        return Result.success(labelTemplateService.detail(id));
    }

    @GetMapping("/default")
    @RequirePermission(value = "label:template:list", message = "您没有权限查看默认标签模板")
    public Result<LabelTemplateVO> defaultTemplate(@RequestParam(required = false, defaultValue = "label") String printType) {
        return Result.success(labelTemplateService.defaultTemplate(printType));
    }

    @PostMapping("/save")
    @RequirePermission(value = "label:template:save", message = "您没有权限保存标签模板")
    public Result<LabelTemplateVO> save(@Valid @RequestBody LabelTemplateSaveRequest request) {
        return Result.success(labelTemplateService.save(request));
    }

    @PostMapping("/upload")
    @RequirePermission(value = "label:template:upload", message = "您没有权限上传标签模板")
    public Result<LabelTemplateVO> upload(@RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false, defaultValue = "label") String printType,
                                          @RequestParam(required = false, defaultValue = "0") Integer isDefault) {
        return Result.success(labelTemplateService.upload(file, name, printType, isDefault));
    }

    @PostMapping("/{id}/default")
    @RequirePermission(value = "label:template:default", message = "您没有权限设置默认标签模板")
    public Result<Void> setDefault(@PathVariable Long id) {
        labelTemplateService.setDefault(id);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(value = "label:template:disable", message = "您没有权限停用标签模板")
    public Result<Void> disable(@PathVariable Long id) {
        labelTemplateService.disable(id);
        return Result.success(null);
    }
}
