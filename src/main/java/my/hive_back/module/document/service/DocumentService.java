package my.hive_back.module.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.common.enums.DeleteFlagEnum;
import my.hive_back.module.document.DocumentUploadStatusEnum;
import my.hive_back.common.storage.FileUploadResult;
import my.hive_back.common.storage.OssStorageService;
import my.hive_back.module.document.DocumentTypeEnum;
import my.hive_back.module.document.mapper.DocumentMapper;
import my.hive_back.module.document.model.dto.DocumentAddRequest;
import my.hive_back.module.document.model.entity.Document;
import my.hive_back.module.document.model.vo.DocumentVO;
import my.hive_back.module.tenant.mapper.TenantMapper;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DocumentService {

    private static final int MAX_TREE_DEPTH = 20;
    private static final int MAX_NAME_LENGTH = 180;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private TenantMapper tenantMapper;

    public List<Document> selectDocumentByParentId(Long parentId) {
        String tenantCode = requireTenantCode();
        Long normalizedParentId = normalizeParentId(parentId);
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getParentId, normalizedParentId);
        queryWrapper.eq(Document::getIsDeleted, DeleteFlagEnum.NORMAL.getCode());
        queryWrapper.orderByAsc(Document::getType);
        queryWrapper.orderByAsc(Document::getCreateTime);
        return documentMapper.selectList(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFolder(DocumentAddRequest request) {
        if (request == null) {
            throw new BusinessException("文件夹参数不能为空");
        }
        String tenantCode = requireTenantCode();
        Long parentId = normalizeParentId(request.getParentId());
        String folderName = normalizeName(request.getName(), "文件夹名称");
        ensureParentFolder(parentId);
        ensureNameNotExists(tenantCode, parentId, folderName, null);

        Document document = new Document();
        document.setName(folderName);
        document.setOriginalName(folderName);
        document.setParentId(parentId);
        document.setType(DocumentTypeEnum.FOLDER.getType());
        document.setTenantCode(tenantCode);
        document.setCreatorId(TenantPermissionContext.getUserId());
        documentMapper.insert(document);
    }

    public DocumentVO uploadFile(MultipartFile file, Long parentId) {
        String tenantCode = requireTenantCode();
        Long normalizedParentId = normalizeParentId(parentId);
        ensureParentFolder(normalizedParentId);

        String displayName = normalizeUploadFilename(file);
        ensureNameNotExists(tenantCode, normalizedParentId, displayName, null);
        ensureStorageQuota(tenantCode, file.getSize());

        FileUploadResult uploadResult = ossStorageService.upload(file, tenantCode);
        Document document = new Document();
        document.setTenantCode(tenantCode);
        document.setParentId(normalizedParentId);
        document.setName(displayName);
        document.setOriginalName(uploadResult.getOriginalName());
        document.setType(DocumentTypeEnum.FILE.getType());
        document.setFileUrl(uploadResult.getUrl());
        document.setStorageProvider(uploadResult.getStorageProvider());
        document.setStorageBucket(uploadResult.getBucketName());
        document.setStorageObjectKey(uploadResult.getObjectKey());
        document.setFileSize(uploadResult.getFileSize());
        document.setFileExt(uploadResult.getFileExt());
        document.setMimeType(uploadResult.getMimeType());
        document.setFileHash(uploadResult.getFileHash());
        document.setEtag(uploadResult.getEtag());
        document.setUploadStatus(DocumentUploadStatusEnum.UPLOADED.getCode());
        document.setCreatorId(TenantPermissionContext.getUserId());

        try {
            documentMapper.insert(document);
            return toVO(document);
        } catch (RuntimeException e) {
            ossStorageService.deleteQuietly(uploadResult.getObjectKey());
            log.error("save document after oss upload failed, tenantCode={}, objectKey={}", tenantCode, uploadResult.getObjectKey(), e);
            throw e;
        }
    }

    public void renameDocument(Long documentId, String newName) {
        Document document = requireDocument(documentId);
        String normalizedName = normalizeName(newName, "文档名称");
        ensureNameNotExists(document.getTenantCode(), document.getParentId(), normalizedName, documentId);
        document.setName(normalizedName);
        documentMapper.updateById(document);
    }

    public void moveDocument(Long documentId, Long targetParentId) {
        Document currentDoc = requireDocument(documentId);
        Long normalizedTargetParentId = normalizeParentId(targetParentId);
        if (documentId.equals(normalizedTargetParentId)) {
            throw new BusinessException("目标位置不能是自身");
        }

        ensureParentFolder(normalizedTargetParentId);
        ensureNameNotExists(currentDoc.getTenantCode(), normalizedTargetParentId, currentDoc.getName(), currentDoc.getId());

        if (DocumentTypeEnum.FOLDER.getType().equals(currentDoc.getType()) && normalizedTargetParentId > 0) {
            Long checkId = normalizedTargetParentId;
            int depth = 0;
            while (checkId != null && checkId > 0 && depth < MAX_TREE_DEPTH) {
                if (checkId.equals(documentId)) {
                    throw new BusinessException("不能将文件夹移动到自己的子文件夹中");
                }
                Document checkDoc = requireDocument(checkId);
                checkId = checkDoc.getParentId();
                depth++;
            }
        }

        currentDoc.setParentId(normalizedTargetParentId);
        documentMapper.updateById(currentDoc);
    }

    public List<DocumentVO> getBreadcrumbs(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return Collections.emptyList();
        }

        List<DocumentVO> breadcrumbs = new ArrayList<>();
        Long currentId = documentId;
        int depth = 0;

        while (currentId != null && currentId > 0 && depth < MAX_TREE_DEPTH) {
            Document document = documentMapper.selectById(currentId);
            if (document == null || !requireTenantCode().equals(document.getTenantCode())) {
                break;
            }
            breadcrumbs.add(toVO(document));
            currentId = document.getParentId();
            depth++;
        }

        if (depth >= MAX_TREE_DEPTH) {
            log.warn("document breadcrumbs reached max depth, documentId={}", documentId);
        }

        Collections.reverse(breadcrumbs);
        return breadcrumbs;
    }

    private DocumentVO toVO(Document document) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(document, vo);
        return vo;
    }

    private void ensureParentFolder(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        Document parent = requireDocument(parentId);
        if (!DocumentTypeEnum.FOLDER.getType().equals(parent.getType())) {
            throw new BusinessException("目标目录不是文件夹");
        }
    }

    private Document requireDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID不合法");
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null || !requireTenantCode().equals(document.getTenantCode())) {
            throw new BusinessException("文档不存在");
        }
        return document;
    }

    private void ensureNameNotExists(String tenantCode, Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getParentId, parentId);
        queryWrapper.eq(Document::getName, name);
        queryWrapper.eq(Document::getIsDeleted, DeleteFlagEnum.NORMAL.getCode());
        if (excludeId != null && excludeId > 0) {
            queryWrapper.ne(Document::getId, excludeId);
        }
        queryWrapper.last("LIMIT 1");
        if (documentMapper.selectOne(queryWrapper) != null) {
            throw new BusinessException("同目录下已存在同名文档");
        }
    }

    private void ensureStorageQuota(String tenantCode, long appendBytes) {
        if (appendBytes <= 0) {
            throw new BusinessException("文件内容为空，无法上传");
        }
        Tenant tenant = tenantMapper.selectByTenantCode(tenantCode);
        if (tenant == null) {
            throw new BusinessException("租户不存在，无法上传文件");
        }
        Integer maxStorageMb = tenant.getMaxStorageMb();
        if (maxStorageMb == null) {
            return;
        }
        if (maxStorageMb <= 0) {
            throw new BusinessException("当前套餐未开通文件存储空间");
        }
        long maxBytes = maxStorageMb * 1024L * 1024L;
        Long used = documentMapper.sumActiveFileSize(tenantCode);
        long usedBytes = used == null ? 0L : used;
        if (Long.MAX_VALUE - usedBytes < appendBytes || usedBytes + appendBytes > maxBytes) {
            throw new BusinessException("租户文件存储空间不足，请升级套餐或清理历史文件");
        }
    }

    private String normalizeUploadFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择需要上传的文件");
        }
        return normalizeName(file.getOriginalFilename(), "文件名");
    }

    private String normalizeName(String name, String label) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(label + "不能为空");
        }
        String normalized = org.springframework.util.StringUtils.cleanPath(name.trim());
        if (normalized.contains("..") || normalized.contains("/") || normalized.contains("\\")) {
            throw new BusinessException(label + "不合法");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(label + "不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return normalized;
    }

    private Long normalizeParentId(Long parentId) {
        if (parentId == null) {
            return 0L;
        }
        if (parentId < 0) {
            throw new BusinessException("父目录ID不合法");
        }
        return parentId;
    }

    private String requireTenantCode() {
        String tenantCode = TenantPermissionContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new BusinessException("租户信息缺失");
        }
        return tenantCode;
    }
}
