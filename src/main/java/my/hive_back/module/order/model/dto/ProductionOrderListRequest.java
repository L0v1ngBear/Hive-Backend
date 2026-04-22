package my.hive_back.module.order.model.dto;

import lombok.Data;

/**
 * 生产订单列表查询入参。
 *
 * <p>当前生产订单列表没有独立于 {@link BaseOrderListRequest} 的额外筛选字段，
 * 因此类体暂时为空，只保留独立类型，方便 Controller/Service 语义清晰。
 * 后续如果要增加车间、工序、生产负责人等筛选条件，可以直接加在这里。</p>
 */
@Data
public class ProductionOrderListRequest extends BaseOrderListRequest {
}
