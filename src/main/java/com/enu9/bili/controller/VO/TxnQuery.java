package com.enu9.bili.controller.VO;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TxnQuery {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime start;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime end;

    // getter / setter
    private String tradeType;
    private String direction;     // INCOME/EXPENSE/NEUTRAL
    private String status;
    private String payMethod;
    private String counterparty;  // 模糊查询
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    private Integer page = 1;
    private Integer size = 20;
}
