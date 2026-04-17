package my.hive_back.module.document.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
/**
 * Document 属于小程序后端单据模块，定义持久化实体结构，用于表字段映射。
 */
@Data
public class Document implements Serializable {


    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)  // 自增主键
    private Long id;

    /**
     * 租户编码
     */
    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 父级ID (0表示根目录)
     */
    @TableField("parent_id")
    private Long parentId = 0L;  // 默认值0

    /**
     * 文件或文件夹名称
     */
    @TableField("name")
    private String name;

    /**
     * 节点类型: 1-文件夹, 2-文件
     * 建议后续封装为枚举类使用，例如：
     * public enum DocumentType { FOLDER(1), FILE(2); }
     */
    @TableField("type")
    private Integer type;

    /**
     * 文件存储路径 (OSS/MinIO等真实链接)
     * 文件夹此字段为空
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 文件大小 (字节)
     * 文件夹此字段为空
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 文件后缀扩展名 (如 pdf, docx)
     * 文件夹此字段为空
     */
    @TableField("file_ext")
    private String fileExt;

    /**
     * 媒体类型 (如 application/pdf)
     * 文件夹此字段为空
     */
    @TableField("mime_type")
    private String mimeType;

    /**
     * 创建人ID
     */
    @TableField("creator_id")
    private Long creatorId;

    /**
     * 创建时间
     * 自动填充，无需手动设置
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     * 自动填充，无需手动设置
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除: 0-正常, 1-已删除
     * MyBatis-Plus逻辑删除注解，自动过滤已删除数据
     */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted = 0;  // 默认值0
}
