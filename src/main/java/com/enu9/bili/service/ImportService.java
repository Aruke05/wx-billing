package com.enu9.bili.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.enu9.bili.DO.ImportBatch;
import com.enu9.bili.DO.WxDict;
import com.enu9.bili.DO.WxExcelRow;
import com.enu9.bili.DO.WxPayTxn;
import com.enu9.bili.mapper.ImportBatchMapper;
import com.enu9.bili.mapper.WxPayTxnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.ListUtils.partition;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final WxPayTxnMapper txnMapper;
    private final ImportBatchMapper batchMapper;

    @Transactional
    public ImportBatch importWeChatExcel(MultipartFile file, String uploadedBy) throws IOException {
        ImportBatch batch = new ImportBatch();
        batch.setFileName(file.getOriginalFilename());
        batch.setUploadedBy(uploadedBy);
        batch.setCreatedAt(LocalDateTime.now());
        batchMapper.insert(batch);

        List<WxPayTxn> parsed = parseExcel(file.getInputStream(), batch.getId());

        if (parsed.isEmpty()) {
            batch.setRecordCount(0);
            batch.setInsertedCount(0);
            batch.setDuplicatedCount(0);
            batchMapper.updateById(batch);
            return batch;
        }

        // 统计期起止
        LocalDateTime minT = parsed.stream().map(WxPayTxn::getTradeTime).min(LocalDateTime::compareTo).get();
        LocalDateTime maxT = parsed.stream().map(WxPayTxn::getTradeTime).max(LocalDateTime::compareTo).get();
        batch.setPeriodStart(minT);
        batch.setPeriodEnd(maxT);
        batch.setRecordCount(parsed.size());

        // 去重：按 order_id 过滤已存在
        List<String> orderIds = parsed.stream()
                .map(WxPayTxn::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (!orderIds.isEmpty()) {
            Set<String> exists = new HashSet<>();
            for (List<String> chunk : partition(orderIds, 800)) {
                QueryWrapper<WxPayTxn> qw = new QueryWrapper<>();
                qw.select("order_id").in("order_id", chunk);
                txnMapper.selectList(qw).forEach(e -> exists.add(e.getOrderId()));
            }
            parsed = parsed.stream()
                    .filter(t -> !exists.contains(t.getOrderId()))
                    .collect(Collectors.toList());
            batch.setDuplicatedCount(orderIds.size() - parsed.size());
        }
        // 批量入库
        for (WxPayTxn t : parsed) {
            txnMapper.insert(t);
        }
        batch.setInsertedCount(parsed.size());
        batchMapper.updateById(batch);
        return batch;
    }

    /** 用 EasyExcel 逐行读取，自动识别标题行后再按列位解析 */
    /** 用 EasyExcel 逐行读取，自动识别标题行后再按列位解析（修复 Stream Closed） */
    private List<WxPayTxn> parseExcel(InputStream in, Long batchId) throws IOException {
        // ---------- 把上传流完整读入内存（Java 8 兼容写法） ----------
        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(16 * 1024)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            bytes = baos.toByteArray();
        }
        if (bytes.length == 0) {
            throw new IllegalStateException("上传的Excel为空文件");
        }

        // ---------- 第一遍：定位表头所在的“行号”（从0开始） ----------
        AtomicInteger headerRowIndex = new AtomicInteger(-1);

        AnalysisEventListener<Map<Integer, String>> headerFinder =
                new AnalysisEventListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> row, AnalysisContext context) {
                        if (contains(row, "交易时间")
                                && contains(row, "交易类型")
                                && contains(row, "交易对方")) {
                            headerRowIndex.set(context.readRowHolder().getRowIndex());
                            // 找到表头后立即停止第一遍扫描，避免读完整个文件
                            throw new ExcelAnalysisStopException();
                        }
                    }
                    @Override public void doAfterAllAnalysed(AnalysisContext context) {}

                    private boolean contains(Map<Integer, String> row, String target) {
                        for (String v : row.values()) {
                            if (v != null) {
                                // 去掉不间断空格、首尾空格，防止“看起来一样实际不同”的情况
                                String norm = v.replace('\u00A0', ' ').trim();
                                if (target.equals(norm)) return true;
                            }
                        }
                        return false;
                    }
                };

        try {
            EasyExcel.read(new ByteArrayInputStream(bytes), headerFinder).sheet().doRead();
        } catch (ExcelAnalysisStopException ignore) {
            // 这是我们主动抛的，用来提前结束扫描
        }

        if (headerRowIndex.get() == -1) {
            throw new IllegalStateException("未找到表头行，请检查Excel文件格式！");
        }

        // ⚠️ headRowNumber 是“表头行数”不是“行号”，所以要 +1
        int headRowNum = headerRowIndex.get() + 1;

        // ---------- 第二遍：按实体类映射读取真正的数据 ----------
        List<WxPayTxn> result = new ArrayList<>();

        AnalysisEventListener<WxExcelRow> dataListener =
                new AnalysisEventListener<WxExcelRow>() {
                    @Override
                    public void invoke(WxExcelRow row, AnalysisContext context) {
                        try {
                            String tradeTimeStr = trimToNull(row.getTradeTime());
                            if (tradeTimeStr == null) return;

                            LocalDateTime tt = WxDict.parseTime(tradeTimeStr);

                            WxPayTxn t = new WxPayTxn();
                            t.setTradeTime(tt);
                            t.setTradeDate(tt.toLocalDate());
                            t.setTradeHour(tt.getHour());
                            t.setWeekday(tt.getDayOfWeek().getValue());

                            t.setTradeType(trim(row.getTradeType()));
                            t.setCounterparty(trim(row.getCounterparty()));
                            t.setProduct(trim(row.getProduct()));
                            t.setDirection(WxDict.toDirection(row.getDirection()));
                            t.setAmount(WxDict.parseAmount(row.getAmount()));
                            t.setPayMethod(trim(row.getPayMethod()));
                            t.setStatus(trim(row.getStatus()));
                            t.setOrderId(trim(row.getOrderId()));
                            t.setMerchantOrderId(trim(row.getMerchantOrderId()));
                            t.setRemark(trim(row.getRemark()));

                            t.setImportBatchId(batchId);
                            t.setCreatedAt(LocalDateTime.now());
                            result.add(t);
                        } catch (Exception e) {
                            // 建议换成日志：logger.warn("解析失败, row={}, err={}", row, e.getMessage());
                            System.err.println("解析失败: " + row + " 错误: " + e.getMessage());
                        }
                    }
                    @Override public void doAfterAllAnalysed(AnalysisContext context) {}

                    private String trim(String s) { return s == null ? null : s.trim(); }
                    private String trimToNull(String s) {
                        if (s == null) return null;
                        String x = s.trim();
                        return x.isEmpty() ? null : x;
                    }
                };

        EasyExcel.read(new ByteArrayInputStream(bytes), WxExcelRow.class, dataListener)
                .sheet()
                .headRowNumber(headRowNum)
                .doRead();

        in.close();
        return result;
    }

}
