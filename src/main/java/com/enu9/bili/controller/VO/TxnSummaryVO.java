package com.enu9.bili.controller.VO;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TxnSummaryVO {
    private BigDecimal income = BigDecimal.ZERO;     // 总收入
    private BigDecimal expense = BigDecimal.ZERO;    // 总支出
    private BigDecimal net = BigDecimal.ZERO;        // 净额
    private Integer incomeCount = 0;                 // 收入笔数
    private Integer expenseCount = 0;                // 支出笔数
    private BigDecimal avgIncome = BigDecimal.ZERO;  // 平均单笔收入
}
