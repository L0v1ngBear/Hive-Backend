package my.hive_back.module.badproduct.model.dto;

import lombok.Data;

/**
 * 质量记录列表分页查询入参。
 *
 * <p>该接口与管理端保持同一套请求契约：
 * pageNum/pageSize/status/type/date，空筛选字段不参与查询。</p>
 */
@Data
public class BadProductPageRequest {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    private String status;

    private String type;

    /**
     * 业务视图：quality=质量记录，afterSales=售后管理。
     */
    private String businessScope;

    private String date;
}
