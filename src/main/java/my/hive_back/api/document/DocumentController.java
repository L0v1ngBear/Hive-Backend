package my.hive_back.api.document;

import jakarta.annotation.Resource;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.Result;
import my.hive_back.module.document.model.dto.DocumentAddRequest;
import my.hive_back.module.document.model.entity.Document;
import my.hive_back.module.document.model.vo.DocumentVO;
import my.hive_back.module.document.service.DocumentService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;
/**
 * DocumentController handles document requests for the mini-program backend and delegates to services.
 */
@Validated
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @GetMapping("/list/{parentId}")
    @RequirePermission(value = "document:list", message = "您没有权限查看文档列表")
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
    @RequirePermission(value = "document:folder:create", message = "您没有权限创建文件夹")
    public Result<Void> createFolder(@RequestBody DocumentAddRequest request) {
        documentService.addFolder(request);
        return Result.success(null);
    }

    @PostMapping("/file/upload")
    @RequirePermission(value = "document:file:upload", message = "您没有权限上传文件")
    public Result<Void> uploadFile(@RequestParam("file") MultipartFile file) {
        documentService.uploadFile(file);
        return Result.success(null);
    }

    @PutMapping("rename")
    @RequirePermission(value = "document:rename", message = "您没有权限重命名文档")
    public Result<Void> renameDocument(@RequestParam Long documentId, @RequestParam String newName) {
        documentService.renameDocument(documentId, newName);
        return Result.success(null);
    }

    @PutMapping("move")
    @RequirePermission(value = "document:move", message = "您没有权限移动文档")
    public Result<Void> moveDocument(@RequestParam Long documentId, @RequestParam Long newParentId) {
        documentService.moveDocument(documentId, newParentId);
        return Result.success(null);
    }

    @GetMapping("breadcrumbs")
    @RequirePermission(value = "document:breadcrumbs", message = "您没有权限查看文档面包屑")
    public Result<List<DocumentVO>> breadcrumbsBreadcrumbs(@RequestParam Long documentId) {
        List<DocumentVO> breadcrumbs = documentService.getBreadcrumbs(documentId);
        return Result.success(breadcrumbs);
    }
}
