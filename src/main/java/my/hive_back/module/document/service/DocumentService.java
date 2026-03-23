package my.hive_back.module.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.document.DocumentTypeEnum;
import my.hive_back.module.document.mapper.DocumentMapper;
import my.hive_back.module.document.model.dto.DocumentAddRequest;
import my.hive_back.module.document.model.entity.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DocumentService {

    @Resource
    private DocumentMapper documentMapper;

    public List<Document> selectDocumentByParentId(Long parentId) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getParentId, parentId);
        queryWrapper.orderByAsc(Document::getType);
        queryWrapper.orderByAsc(Document::getCreateTime);
        return documentMapper.selectList(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFolder(DocumentAddRequest request) {

        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getParentId, request.getParentId());
        queryWrapper.eq(Document::getName, request.getName());

        if (documentMapper.selectOne(queryWrapper) != null) {
            throw new BusinessException("文件夹名称已存在");
        }

        insertFolder(request);
    }

    @Transactional(rollbackFor = Exception.class)
    protected void insertFolder(DocumentAddRequest request) {
        Document document = new Document();
        document.setName(request.getName());
        document.setParentId(request.getParentId());
        document.setType(DocumentTypeEnum.FOLDER.getType());
        document.setTenantCode(TenantPermissionContext.getTenantCode());
        documentMapper.insert(document);
    }

    public void uploadFile(MultipartFile file) {
        // TODO 接入阿里云oss
    }
}
