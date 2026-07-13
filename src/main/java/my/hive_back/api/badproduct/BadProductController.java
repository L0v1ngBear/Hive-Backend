package my.hive_back.api.badproduct;

import my.hive_back.module.tenant.TenantFeatureEnum;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive.common.annotation.CollectLog;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.common.storage.BusinessImageAttachmentService;
import my.hive_back.common.storage.BusinessImageAttachmentVO;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.badproduct.model.dto.BadProductPageRequest;
import my.hive_back.module.badproduct.model.dto.BadProductProcessRequest;
import my.hive_back.module.badproduct.model.dto.BadProductSaveRequest;
import my.hive_back.module.badproduct.model.vo.BadProductVO;
import my.hive_back.module.badproduct.service.BadProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 小程序质量管理控制器，负责列表查询、登记和处理。
 */
@RestController
@RequestMapping("/bad-product")
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_BAD_PRODUCT)
public class BadProductController {

    @Resource
    private BadProductService badProductService;

    @Resource
    private BusinessImageAttachmentService businessImageAttachmentService;

    @GetMapping("/list")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_LIST, message = "您没有权限查看质量记录列表")
    public Result<PageResult<BadProductVO>> list(BadProductPageRequest request) {
        return Result.success(badProductService.page(request));
    }

    @PostMapping("/save")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_SAVE, message = "您没有权限登记质量记录")
    @CollectLog(module = "bad_product", action = "mini_save", bizType = "bad_product", bizNo = "#request.defectiveId", description = "小程序登记质量记录")
    public Result<Void> save(@Valid @RequestBody BadProductSaveRequest request) {
        badProductService.save(request);
        return Result.success(null);
    }

    @PostMapping("/attachment/upload")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_SAVE, message = "您没有权限上传质量图片")
    @CollectLog(module = "bad_product", action = "mini_upload_image", bizType = "bad_product", description = "小程序上传质量图片")
    public Result<BusinessImageAttachmentVO> uploadAttachment(@RequestParam("file") MultipartFile file) {
        return Result.success(businessImageAttachmentService.uploadImage(file, "bad-product"));
    }

    @GetMapping("/attachment/download")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_LIST, message = "您没有权限查看质量图片")
    public ResponseEntity<org.springframework.core.io.Resource> downloadAttachment(@RequestParam("url") String url) {
        org.springframework.core.io.Resource resource = businessImageAttachmentService.load(url, "bad-product");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    @PostMapping("/process")
    @RequirePermission(value = PermissionCodeEnum.CODE_BADPRODUCT_PROCESS, message = "您没有权限处理质量记录")
    @CollectLog(module = "bad_product", action = "mini_process", bizType = "bad_product", bizNo = "#request.defectiveId", description = "小程序处理质量记录")
    public Result<Void> process(@Valid @RequestBody BadProductProcessRequest request) {
        badProductService.process(request);
        return Result.success(null);
    }
}
