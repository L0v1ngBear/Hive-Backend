package my.hive_back.api.document;

import my.hive_back.module.tenant.TenantFeatureEnum;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import jakarta.annotation.Resource;
import my.hive.common.annotation.CollectLog;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.document.model.dto.DocumentAddRequest;
import my.hive_back.module.document.model.entity.Document;
import my.hive_back.module.document.model.vo.DocumentVO;
import my.hive_back.module.document.service.DocumentService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/document")
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_DOCUMENT)
@Validated
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @GetMapping("/list/{parentId}")
    @RequirePermission(value = PermissionCodeEnum.CODE_DOCUMENT_LIST, message = "您没有权限查看文档列表")
    public Result<List<DocumentVO>> list(@PathVariable Long parentId) {
        List<Document> documentList = documentService.selectDocumentByParentId(parentId);
        List<DocumentVO> documentVOList = documentList.stream().map(doc -> {
            DocumentVO documentVO = new DocumentVO();
            BeanUtils.copyProperties(doc, documentVO);
            return documentVO;
        }).collect(Collectors.toList());
        return Result.success(documentVOList);
    }

    @PostMapping("/folder/create")
    @RequirePermission(value = PermissionCodeEnum.CODE_DOCUMENT_FOLDER_CREATE, message = "您没有权限创建文件夹")
    @CollectLog(module = "document", action = "mini_create_folder", bizType = "document", bizNo = "#request.name", description = "小程序创建文档文件夹")
    public Result<Void> createFolder(@RequestBody DocumentAddRequest request) {
        documentService.addFolder(request);
        return Result.success(null);
    }

    @PostMapping("/file/upload")
    @RequirePermission(value = PermissionCodeEnum.CODE_DOCUMENT_FILE_UPLOAD, message = "您没有权限上传文件")
    @CollectLog(module = "document", action = "mini_upload_file", bizType = "document", description = "小程序上传文档")
    public Result<DocumentVO> uploadFile(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId) {
        return Result.success(documentService.uploadFile(file, parentId));
    }

    @PutMapping("/rename")
    @RequirePermission(value = PermissionCodeEnum.CODE_DOCUMENT_RENAME, message = "您没有权限重命名文档")
    @CollectLog(module = "document", action = "mini_rename", bizType = "document", bizNo = "#documentId", description = "小程序重命名文档")
    public Result<Void> renameDocument(@RequestParam Long documentId, @RequestParam String newName) {
        documentService.renameDocument(documentId, newName);
        return Result.success(null);
    }

    @PutMapping("/move")
    @RequirePermission(value = PermissionCodeEnum.CODE_DOCUMENT_MOVE, message = "您没有权限移动文档")
    @CollectLog(module = "document", action = "mini_move", bizType = "document", bizNo = "#documentId", description = "小程序移动文档")
    public Result<Void> moveDocument(@RequestParam Long documentId, @RequestParam Long newParentId) {
        documentService.moveDocument(documentId, newParentId);
        return Result.success(null);
    }

    @GetMapping("/breadcrumbs")
    @RequirePermission(value = PermissionCodeEnum.CODE_DOCUMENT_BREADCRUMBS, message = "您没有权限查看文档面包屑")
    public Result<List<DocumentVO>> breadcrumbs(@RequestParam Long documentId) {
        return Result.success(documentService.getBreadcrumbs(documentId));
    }
}
