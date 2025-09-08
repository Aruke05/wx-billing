// package: com.enu9.bili.service.parser
package com.enu9.bili.service.Parser;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.enu9.bili.DO.WxExcelRow;
import com.enu9.bili.DO.payTxn;
import com.enu9.bili.DO.WxDict;

import com.enu9.bili.service.Parser.BillParser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.enu9.config.ExcelUtil.*; // 复用 trim/trimToNull/norm 等

@Component
public class WechatBillParser implements BillParser {

    @Override
    public Channel channel() { return Channel.WECHAT; }

    @Override
    public List<payTxn> parse(byte[] bytes, Long batchId) throws IOException {
        final AtomicInteger headerRowIndex = new AtomicInteger(-1);

        AnalysisEventListener<Map<Integer, String>> headerFinder =
                new AnalysisEventListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> row, AnalysisContext context) {
                        if (contains(row, "交易时间") && contains(row, "交易类型") && contains(row, "交易对方")) {
                            headerRowIndex.set(context.readRowHolder().getRowIndex());
                            throw new ExcelAnalysisStopException();
                        }
                    }
                    @Override public void doAfterAllAnalysed(AnalysisContext context) {}
                };

        try { EasyExcel.read(new ByteArrayInputStream(bytes), headerFinder).sheet().doRead(); }
        catch (ExcelAnalysisStopException ignore) {}
        if (headerRowIndex.get() == -1) throw new IllegalStateException("未找到表头行（微信）");

        int headRowNum = headerRowIndex.get() + 1;

        final List<payTxn> result = new ArrayList<>();

        AnalysisEventListener<WxExcelRow> dataListener =
                new AnalysisEventListener<WxExcelRow>() {
                    @Override
                    public void invoke(WxExcelRow row, AnalysisContext context) {
                        try {
                            String tradeTimeStr = trimToNull(row.getTradeTime());
                            if (tradeTimeStr == null) return;
                            LocalDateTime tt = WxDict.parseTime(tradeTimeStr);

                            payTxn t = new payTxn();
                            t.setTradeTime(tt);
                            t.setTradeDate(tt.toLocalDate());
                            t.setTradeHour(tt.getHour());
                            t.setWeekday(tt.getDayOfWeek().getValue());

                            t.setTradeType(trim(row.getTradeType()));
                            t.setCounterparty(trim(row.getCounterparty()));
                            t.setProduct(trim(row.getProduct()));
                            t.setDirection(WxDict.toDirection(row.getDirection()));
                            t.setAmount(WxDict.parseAmount(row.getAmount())); // 正数
                            t.setPayMethod(trim(row.getPayMethod()));
                            t.setStatus(trim(row.getStatus()));
                            t.setOrderId(trim(row.getOrderId()));
                            t.setMerchantOrderId(trim(row.getMerchantOrderId()));
                            t.setRemark(trim(row.getRemark()));

                            t.setImportBatchId(batchId);
                            t.setCreatedAt(LocalDateTime.now());

                            t.setChannelType(Channel.WECHAT.name());
                            result.add(t);
                        } catch (Exception e) {
                            System.err.println("解析失败(微信): " + row + " 错误: " + e.getMessage());
                        }
                    }
                    @Override public void doAfterAllAnalysed(AnalysisContext context) {}
                };

        EasyExcel.read(new ByteArrayInputStream(bytes), WxExcelRow.class, dataListener)
                .sheet().headRowNumber(headRowNum).doRead();

        return result;
    }

    private boolean contains(Map<Integer,String> row, String target) {
        for (String v : row.values()) {
            if (v != null) {
                String norm = v.replace('\u00A0', ' ').trim();
                if (target.equals(norm)) return true;
            }
        }
        return false;
    }
}
