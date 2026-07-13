package my.hive_back.module.order.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 销售订单列表查询入参。
 *
 * <p>当前销售订单列表复用 {@link BaseOrderListRequest} 的通用查询字段，
 * 类体暂时为空是为了保留销售订单自己的扩展入口，例如后续可以追加客户、
 * 项目或是否开票等筛选条件。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderListRequest extends BaseOrderListRequest {
}
