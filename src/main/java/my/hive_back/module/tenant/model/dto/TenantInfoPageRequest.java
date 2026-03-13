package my.hive_back.module.tenant.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 租户信息分页查询请求DTO
 * 适配MyBatis-Plus分页查询，包含基础分页参数和租户查询条件
 */
@Data
public class TenantInfoPageRequest {

    /**
     * 页码（必填，默认1，最小值1）
     */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    /**
     * 页大小（必填，默认10，最小值1，最大值100）
     */
    @NotNull(message = "页大小不能为空")
    @Min(value = 1, message = "页大小不能小于1")
    @Max(value = 100, message = "页大小不能超过100")
    private Integer pageSize = 10;

    /**
     * 租户状态：0-禁用，1-正常，2-冻结（可选，传null则不筛选）
     */
    @Min(value = 0, message = "租户状态值不合法（仅支持0/1/2）")
    @Max(value = 2, message = "租户状态值不合法（仅支持0/1/2）")
    private Integer status;

    /**
     * 租户名称（可选，模糊查询，最大长度64）
     */
    @Size(max = 64, message = "租户名称长度不能超过64个字符")
    private String tenantName;

    /**
     * 逻辑删除标识：0-未删除，1-已删除（可选，默认查询未删除，仅支持0/1）
     */
    @Min(value = 0, message = "删除标识值不合法（仅支持0/1）")
    @Max(value = 1, message = "删除标识值不合法（仅支持0/1）")
    private Integer isDeleted = 0;
}