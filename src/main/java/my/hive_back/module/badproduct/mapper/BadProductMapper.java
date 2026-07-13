package my.hive_back.module.badproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import my.hive_back.module.badproduct.model.entity.BadProductRecord;
import org.apache.ibatis.annotations.Mapper;
/**
 * BadProductMapper 属于小程序后端坏品模块，是数据访问类，负责与数据库交互。
 */
@Mapper
public interface BadProductMapper extends BaseMapper<BadProductRecord> {
}
