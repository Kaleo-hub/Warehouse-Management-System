package com.myproject.wmsserver.common.sequence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单号发号器。
 * 主键是 (biz_type, biz_date) 复合键，所以这里【不能】用 @TableId ——
 * MyBatis-Plus 的 BaseMapper 按主键操作，复合主键用不了，
 * 这个表的读写全部走自定义 SQL（见 SysSequenceMapper.xml）。
 */
@Data
@TableName("sys_sequence")
public class SysSequence {

    private String bizType;

    private String bizDate;

    private Long currentValue;

    private LocalDateTime updateTime;
}