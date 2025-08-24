package com.enu9.bili.DO;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class WxExcelRow {

    @ExcelProperty("交易时间")
    private String tradeTime;

    @ExcelProperty("交易类型")
    private String tradeType;

    @ExcelProperty("交易对方")
    private String counterparty;

    @ExcelProperty("商品")
    private String product;

    @ExcelProperty("收/支")
    private String direction;

    @ExcelProperty("金额(元)")
    private String amount;

    @ExcelProperty("支付方式")
    private String payMethod;

    @ExcelProperty("当前状态")
    private String status;

    @ExcelProperty("交易单号")
    private String orderId;

    @ExcelProperty("商户单号")
    private String merchantOrderId;

    @ExcelProperty("备注")
    private String remark;
}
