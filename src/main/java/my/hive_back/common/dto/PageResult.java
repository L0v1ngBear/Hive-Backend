package my.hive_back.common.dto;

import lombok.Data;

import java.util.List;
/**
 * PageResult 属于小程序后端通用能力层，定义通用传输对象。
 */
@Data
public class PageResult<T> {
    /** 当前页码 */
    private Long current;
    /** 页大小 */
    private Long size;
    /** 总记录数 */
    private Long total;
    /** 总页数 */
    private Long pages;
    /** 数据列表（替代原records） */
    private List<T> data;
}
