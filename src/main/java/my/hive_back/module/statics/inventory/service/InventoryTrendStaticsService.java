package my.hive_back.module.statics.inventory.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.statics.inventory.model.entity.InventoryTrendStatics;
import org.springframework.stereotype.Service;
/**
 * InventoryTrendStaticsService 属于小程序后端统计模块，实现核心业务编排与规则逻辑。
 */
@Service
public class InventoryTrendStaticsService extends ServiceImpl<InventoryTrendStaticsMapper, InventoryTrendStatics> {
}
