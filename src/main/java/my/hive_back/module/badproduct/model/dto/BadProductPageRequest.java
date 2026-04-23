package my.hive_back.module.badproduct.model.dto;

import lombok.Data;

/**
 * 次品列表分页查询入参。
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

    private String date;
}
