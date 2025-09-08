package com.enu9.bili.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pay_txn")
public class payTxn {
    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDateTime tradeTime;
    private LocalDate tradeDate;
    private Integer tradeHour;   // 0-23
    private Integer weekday;     // 1=Mon .. 7=Sun

    private String tradeType;
    private String counterparty;
    private String product;

    private String direction;    // INCOME/EXPENSE/NEUTRAL
    private BigDecimal amount;   // 正数

    private String channelType;
    private String payMethod;
    private String status;

    private String orderId;
    private String merchantOrderId;
    private String remark;

    private Long importBatchId;
    private LocalDateTime createdAt;
}
