package my.hive_back.module.inventory.model.dto;

import lombok.Data;

@Data
public class InventoryPageRequest {

    private Long pageNum = 1L;

    private Long pageSize = 20L;

    private String keyword;

    private Integer status;

    private String timeOrder;
}
