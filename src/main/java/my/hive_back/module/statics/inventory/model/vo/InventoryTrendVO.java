package my.hive_back.module.statics.inventory.model.vo;

import lombok.Data;

import java.util.List;
/**
 * InventoryTrendVO 属于小程序后端统计模块，定义出参结构。
 */
@Data
public class InventoryTrendVO {

    // "data": {
    //    "dates": ["03-23", "03-24", "03-25", "03-26", "03-27", "03-28", "03-29"],
    //    "inMeters": [120, 300, 0, 450, 100, 210, 350],  // 对应每天的入库总米数
    //    "outMeters": [50, 80, 120, 40, 200, 10, 90]     // 对应每天的出库总米数
    //  }
    private List<String> dates;

    private List<Float> inMeters;

    private List<Float> outMeters;
}
