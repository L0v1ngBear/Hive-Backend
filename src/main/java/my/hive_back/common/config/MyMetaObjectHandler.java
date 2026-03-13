package my.hive_back.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 新增时自动填充创建时间
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 填充 create_time（仅为空时填充）
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        // 填充 update_time（新增时和创建时间一致）
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时自动填充更新时间
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 填充 update_time（每次更新都覆盖为当前时间）
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
