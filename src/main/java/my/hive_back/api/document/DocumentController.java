package my.hive_back.api.document;

import jakarta.annotation.Resource;
import my.hive_back.common.dto.ResultDTO;
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

@RestController
@RequestMapping("/document")
@Validated
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @GetMapping("/list/{parentId}")
    public ResultDTO<List<DocumentVO>> list(@PathVariable Long parentId) {
        List<Document> documentList = documentService.selectDocumentByParentId(parentId);
        List<DocumentVO> documentVOList = documentList.stream().map(doc -> {
            DocumentVO documentVO = new DocumentVO();
            BeanUtils.copyProperties(doc, documentVO);
            return documentVO;
        }).collect(Collectors.toList());
        return ResultDTO.success(documentVOList);
    }

    @PostMapping("/folder/create")
    public ResultDTO<Void> createFolder(@RequestBody DocumentAddRequest request) {
        documentService.addFolder(request);
        return ResultDTO.success(null);
    }

    @PostMapping("/file/upload")
    public ResultDTO<Void> uploadFile(@RequestParam("file") MultipartFile file) {
        documentService.uploadFile(file);
        return ResultDTO.success(null);
    }

    @PutMapping("/document/rename")
    public ResultDTO<Void> renameDocument(@RequestParam Long documentId, @RequestParam String newName) {
        documentService.renameDocument(documentId, newName);
        return ResultDTO.success(null);
    }

    @PutMapping("/document/move")
    public ResultDTO<Void> moveDocument(@RequestParam Long documentId, @RequestParam Long newParentId) {
        documentService.moveDocument(documentId, newParentId);
        return ResultDTO.success(null);
    }

    @GetMapping("/document/breadcrumbs")
    public ResultDTO<List<DocumentVO>> breadcrumbsBreadcrumbs(@RequestParam Long documentId) {
        List<DocumentVO> breadcrumbs = documentService.getBreadcrumbs(documentId);
        return ResultDTO.success(breadcrumbs);
    }
}
