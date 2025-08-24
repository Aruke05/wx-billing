package com.enu9.bili.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("import_batch")
public class ImportBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;
    private String uploadedBy;

    private Integer recordCount;
    private Integer insertedCount;
    private Integer duplicatedCount;

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private LocalDateTime createdAt;
}
