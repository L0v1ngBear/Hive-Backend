package my.hive_back.module.badproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.badproduct.model.entity.BadProductRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BadProductMapper extends BaseMapper<BadProductRecord> {
}