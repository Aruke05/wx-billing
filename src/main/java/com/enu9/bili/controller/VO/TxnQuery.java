package com.enu9.bili.controller.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TxnQuery {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime start;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime end;

    // getter / setter
    private String product;
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
