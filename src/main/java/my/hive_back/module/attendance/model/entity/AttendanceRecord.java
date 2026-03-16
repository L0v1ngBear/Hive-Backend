package my.hive_back.module.attendance.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("attendance_record")
@Data
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String punchId;

    private Long userId;

    private String tenantCode;

    /**
     * 打卡类型
     * 1-上班打卡，2-下班打卡（可扩展：3-加班打卡，4-外勤打卡等）
     */
    private Integer punchType;

    /**
     * 员工打卡纬度
     * 高精度存储，避免浮点误差
     */
    private BigDecimal userLat;

    /**
     * 员工打卡经度
     */
    private BigDecimal userLng;


    /**
     * 与公司打卡点的距离（米）
     * 核心字段：用于判断是否在有效范围内
     */
    private BigDecimal distance;

    /**
     * 打卡有效半径（米）
     * 存储打卡时的半径，避免后续公司半径修改影响历史记录
     */
    private Integer radius;

    /**
     * 打卡结果
     * SUCCESS-成功，FAIL-失败（枚举值，便于统计和筛选）
     */
    private String punchResult;

    /**
     * 打卡时间（核心字段）
     */
    private LocalDateTime punchTime;

    /**
     * 打卡位置描述（辅助信息）
     * 可选：可通过逆地理编码API将经纬度转换为地址
     */
    private String address;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录更新时间
     */
    private LocalDateTime updateTime;
}
