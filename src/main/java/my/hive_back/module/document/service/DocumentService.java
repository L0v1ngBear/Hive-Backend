package my.hive_back.module.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.document.DocumentTypeEnum;
import my.hive_back.module.document.mapper.DocumentMapper;
import my.hive_back.module.document.model.dto.DocumentAddRequest;
import my.hive_back.module.document.model.entity.Document;
import my.hive_back.module.document.model.vo.DocumentVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * DocumentService 属于小程序后端单据模块，实现核心业务编排与规则逻辑。
 */
@Slf4j
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
        // 预留阿里云 OSS 接入：
        // 1. 校验文件大小、类型、租户上传权限
        // 2. 生成按 tenantCode/日期 分层的对象存储路径
        // 3. 上传到 OSS 后保存 fileUrl、fileSize、contentType、originalName
        // 4. 回写 document 表，目录结构继续复用当前 parentId 体系
        // 5. 建议同时补签名直传和服务端回调校验，降低大文件占用
        throw new BusinessException("文件上传功能待接入阿里云OSS");
    }

    public void renameDocument(Long documentId, String newName) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("文件不存在");
        }
        Long parentId = document.getParentId();
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getParentId, parentId);
        queryWrapper.eq(Document::getName, newName);
        Document oldDocument = documentMapper.selectOne(queryWrapper);
        if (oldDocument == null) {
            document.setName(newName);
            documentMapper.updateById(document);
        } else {
            throw new BusinessException("文件名称已存在");
        }
    }

    public void moveDocument(Long documentId, Long targetParentId) {
        // 1. 基础拦截：不能原地踏步
        if (documentId.equals(targetParentId)) {
            throw new BusinessException("目标位置不能是自身");
        }

        Document currentDoc = documentMapper.selectById(documentId);
        if (currentDoc == null) {
            throw new BusinessException("要移动的节点不存在");
        }

        // 2. 目标节点校验
        if (targetParentId != null && targetParentId != 0L) {
            Document targetDoc = documentMapper.selectById(targetParentId);
            if (targetDoc == null) {
                throw new BusinessException("目标文件夹不存在");
            }
            if (!DocumentTypeEnum.FOLDER.getType().equals(targetDoc.getType())) {
                throw new BusinessException("目标位置不是文件夹，无法移入");
            }
        }

        // 3. 高级校验：防死循环（仅当前移动的是文件夹，且目标不是根目录时需要校验）
        if (DocumentTypeEnum.FOLDER.getType().equals(currentDoc.getType()) && targetParentId != null && targetParentId != 0L) {
            Long checkId = targetParentId;
            int maxDepth = 20;
            int depth = 0;

            // 从目标位置往上回溯，看会不会撞见【当前节点】
            while (checkId != null && checkId != 0L && depth < maxDepth) {
                if (checkId.equals(documentId)) {
                    throw new BusinessException("非法操作：不能将文件夹移动到其自身的子文件夹内");
                }

                Document checkDoc = documentMapper.selectById(checkId);
                if (checkDoc == null) {
                    break;
                }
                checkId = checkDoc.getParentId();
                depth++;
            }
        }

        // 4. 更新数据库
        currentDoc.setParentId(targetParentId);

        documentMapper.updateById(currentDoc);
    }



    public List<DocumentVO> getBreadcrumbs(Long documentId) {
        // 1. 判空校验
        if (documentId == null || documentId <= 0) {
            return Collections.emptyList();
        }

        List<DocumentVO> breadcrumbs = new ArrayList<>();
        Long currentId = documentId;

        // 2. 深度限制，防止历史脏数据引发死循环导致 OOM 或 CPU 飙高
        int maxDepth = 20;
        int depth = 0;

        // 3. 核心逻辑：从当前节点不断向上追溯父节点
        while (currentId != null && currentId > 0 && depth < maxDepth) {
            // 通过 MyBatis-Plus 根据主键查询
            Document document = documentMapper.selectById(currentId);

            // 如果查不到数据（例如由于并发被删除了），直接中断
            if (document == null) {
                break;
            }

            // 对象转换封装
            DocumentVO vo = new DocumentVO();
            BeanUtils.copyProperties(document, vo);

            breadcrumbs.add(vo);

            // 指针上移，指向父节点
            currentId = document.getParentId();
            depth++;
        }

        // 4. 边界预警：如果达到了最大深度，记录日志方便排查脏数据
        if (depth >= maxDepth) {
            log.warn("获取面包屑触发最大深度限制，可能存在环状数据或恶意嵌套，起始节点 documentId: {}", documentId);
            // 注：面包屑查询属于展示类功能，达到阈值通常不需要抛出异常阻断用户，直接截断展示即可。
        }

        // 5. 反转列表
        Collections.reverse(breadcrumbs);

        return breadcrumbs;
    }
}
