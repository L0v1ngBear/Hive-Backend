package my.hive_back.module.statics.inventory.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.statics.inventory.model.entity.InventoryTrendStatics;
import org.springframework.stereotype.Service;

@Service
public class InventoryTrendStaticsService extends ServiceImpl<InventoryTrendStaticsMapper, InventoryTrendStatics> {
}