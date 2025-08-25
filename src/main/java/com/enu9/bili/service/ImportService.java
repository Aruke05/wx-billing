package com.enu9.bili.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.enu9.bili.DO.ImportBatch;
import com.enu9.bili.DO.WxExcelRow;
import com.enu9.bili.DO.WxPayTxn;
import com.enu9.bili.DO.WxDict;
import com.enu9.bili.mapper.ImportBatchMapper;
import com.enu9.bili.mapper.WxPayTxnMapper;
import com.enu9.config.ExcelUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.enu9.config.ExcelUtil.*;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final WxPayTxnMapper txnMapper;
    private final ImportBatchMapper batchMapper;

    /** 账单来源渠道 */
    private enum Channel { WECHAT, ALIPAY }

    // --------------- 对外入口（新） ---------------
    @Transactional
    public ImportBatch importExcel(MultipartFile file, String uploadedBy) throws IOException {
        ImportBatch batch = new ImportBatch();
        batch.setFileName(file.getOriginalFilename());
        batch.setUploadedBy(uploadedBy);
        batch.setCreatedAt(LocalDateTime.now());
        batchMapper.insert(batch);

        byte[] bytes = toBytes(file.getInputStream());
        if (bytes.length == 0) {
            throw new IllegalStateException("上传的Excel为空文件");
        }

        Channel ch = detectChannel(batch.getFileName(), bytes);

        List<WxPayTxn> parsed;
        if (ch == Channel.ALIPAY) {
            parsed = parseAlipayExcel(new ByteArrayInputStream(bytes), batch.getId());
        } else {
            parsed = parseWeChatExcel(new ByteArrayInputStream(bytes), batch.getId());
        }

        if (parsed.isEmpty()) {
            batch.setRecordCount(0);
            batch.setInsertedCount(0);
            batch.setDuplicatedCount(0);
            batchMapper.updateById(batch);
            return batch;
        }

        // 期起止与总记录数
        LocalDateTime minT = parsed.stream().map(WxPayTxn::getTradeTime).min(LocalDateTime::compareTo).get();
        LocalDateTime maxT = parsed.stream().map(WxPayTxn::getTradeTime).max(LocalDateTime::compareTo).get();
        batch.setPeriodStart(minT);
        batch.setPeriodEnd(maxT);
        batch.setRecordCount(parsed.size());

        // -------------- 去重：先文件内，再库内（仅非空 orderId） --------------
        int fileDup = 0;
        Map<String, WxPayTxn> byOrderId = new LinkedHashMap<>();
        for (WxPayTxn t : parsed) {
            String oid = trimToNull(t.getOrderId());
            if (oid == null) continue; // 无单号不参与 orderId 去重
            if (byOrderId.putIfAbsent(oid, t) != null) {
                fileDup++;
            }
        }
        // 文件内去重后的保留集合（含无 orderId 的记录）
        List<WxPayTxn> keep = new ArrayList<>();
        // 先保留无 orderId 的记录
        for (WxPayTxn t : parsed) {
            if (trimToNull(t.getOrderId()) == null) keep.add(t);
        }
        // 再保留有 orderId 的唯一记录
        keep.addAll(byOrderId.values());

        // 库内去重
        int dbDup = 0;
        Set<String> uniqueOids = byOrderId.keySet();
        if (!uniqueOids.isEmpty()) {
            Set<String> exists = new HashSet<>();
            List<String> oids = new ArrayList<>(uniqueOids);
            for (List<String> chunk : chunks(oids, 800)) {
                if (chunk.isEmpty()) continue;
                QueryWrapper<WxPayTxn> qw = new QueryWrapper<>();
                qw.select("order_id").in("order_id", chunk);
                List<WxPayTxn> hit = txnMapper.selectList(qw);
                for (WxPayTxn e : hit) {
                    if (e.getOrderId() != null) exists.add(e.getOrderId());
                }
            }
            dbDup = (int) uniqueOids.stream().filter(exists::contains).count();
            // 过滤：去掉库里已有的 orderId
            keep = keep.stream().filter(t -> {
                String oid = trimToNull(t.getOrderId());
                return oid == null || !exists.contains(oid);
            }).collect(Collectors.toList());
        }

        batch.setDuplicatedCount(fileDup + dbDup);

        // 入库（逐条）
        for (WxPayTxn t : keep) {
            txnMapper.insert(t);
        }
        batch.setInsertedCount(keep.size());
        batchMapper.updateById(batch);
        return batch;
    }

    // --------------- 兼容旧方法（内部转调新方法） ---------------
    @Transactional
    public ImportBatch importWeChatExcel(MultipartFile file, String uploadedBy) throws IOException {
        return importExcel(file, uploadedBy);
    }

    // --------------- 渠道识别（文件名优先，表头兜底） ---------------
    private Channel detectChannel(String fileName, byte[] bytes) {
        String fn = fileName == null ? "" : fileName.toLowerCase();
        if (fn.contains("wechat") || fn.contains("微信")) return Channel.WECHAT;
        if (fn.contains("alipay") || fn.contains("支付宝")) return Channel.ALIPAY;

        final AtomicBoolean isAli = new AtomicBoolean(false);
        AnalysisEventListener<Map<Integer, String>> header = new AnalysisEventListener<Map<Integer, String>>() {
            @Override public void invoke(Map<Integer, String> row, AnalysisContext context) {
                String all = String.join(",", row.values()
                        .stream().filter(Objects::nonNull).map(ExcelUtil::norm).collect(Collectors.toList()));
                if (all.contains("交易创建时间") || all.contains("收/支") || all.contains("交易号")) {
                    isAli.set(true);
                    throw new ExcelAnalysisStopException();
                }
                if (all.contains("交易时间") && all.contains("交易类型") && all.contains("交易对方")) {
                    isAli.set(false);
                    throw new ExcelAnalysisStopException();
                }
            }
            @Override public void doAfterAllAnalysed(AnalysisContext context) {}
        };
        try { EasyExcel.read(new ByteArrayInputStream(bytes), header).sheet().doRead(); }
        catch (ExcelAnalysisStopException ignore) {}

        return isAli.get() ? Channel.ALIPAY : Channel.WECHAT;
    }

    // --------------- 微信解析 ---------------
    private List<WxPayTxn> parseWeChatExcel(InputStream in, Long batchId) throws IOException {
        byte[] bytes = toBytes(in);

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

        final List<WxPayTxn> result = new ArrayList<>();

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
                            t.setAmount(WxDict.parseAmount(row.getAmount())); // 保持正数
                            t.setPayMethod(trim(row.getPayMethod()));
                            t.setStatus(trim(row.getStatus()));
                            t.setOrderId(trim(row.getOrderId()));
                            t.setMerchantOrderId(trim(row.getMerchantOrderId()));
                            t.setRemark(trim(row.getRemark()));

                            t.setImportBatchId(batchId);
                            t.setCreatedAt(LocalDateTime.now());
                            result.add(t);
                        } catch (Exception e) {
                            System.err.println("解析失败(微信): " + row + " 错误: " + e.getMessage());
                        }
                    }
                    @Override public void doAfterAllAnalysed(AnalysisContext context) {}
                };

        EasyExcel.read(new ByteArrayInputStream(bytes), WxExcelRow.class, dataListener)
                .sheet().headRowNumber(headRowNum).doRead();

        in.close();
        return result;
    }

    // ======================= 核心：同时支持 XLS/XLSX 与 CSV(GBK/GB18030) =======================
    private List<WxPayTxn> parseAlipayExcel(InputStream in, Long batchId) throws IOException {
        byte[] bytes = toBytes(in);                  // 读入内存
        if (bytes.length == 0) throw new IllegalStateException("上传的Excel为空文件");
        // 1) 先判断是否为 XLSX/XLS（压缩包 zip 头 PK）
        if (isXlsxOrXls(bytes)) {
            return parseAlipayXlsx(bytes, batchId);  // 走 EasyExcel，但不依赖中文表头
        } else {
            return parseAlipayCsv(bytes, batchId);   // 走 CSV + GB18030 解析（彻底无中文乱码）
        }
    }

    // ======================= A. XLS/XLSX 路径（EasyExcel）=======================
    private List<WxPayTxn> parseAlipayXlsx(byte[] bytes, Long batchId) throws IOException {
        final AtomicInteger headerRowIndex = new AtomicInteger(-1);
        final Map<String, Integer> col = new HashMap<String, Integer>();

        // 只做“数据驱动”的兜底：遇到第一条能解析时间的行，认定上一行是表头
        AnalysisEventListener<Map<Integer, String>> headerScan = new AnalysisEventListener<Map<Integer, String>>() {
            @Override public void invoke(Map<Integer, String> row, AnalysisContext context) {
                int r = context.readRowHolder().getRowIndex();
                Integer timeCol = findTimeCol(row);
                if (timeCol != null) {
                    headerRowIndex.set(Math.max(0, r - 1));
                    // 依据“列数量”推断布局（长版 or 简版），并设置常用列位
                    int mx = maxKey(row);
                    if (mx >= 7 && mx <= 8) { // 简版 8 列：时间 对方 商品 方向 金额 方式 状态 交易单号
                        col.put("time", timeCol);
                        col.put("counterparty", timeCol + 1);
                        col.put("product", timeCol + 2);
                        col.put("direction", timeCol + 3);
                        col.put("amount", timeCol + 4);
                        col.put("payMethod", timeCol + 5);
                        col.put("status", timeCol + 6);
                        col.put("orderId", timeCol + 7);
                    } else { // 长版（常见 12 列）：时间 分类 对方 账号 商品 收/支 金额 方式 状态 交易订单号 商家订单号 备注
                        col.put("time", timeCol);
                        col.put("tradeType", timeCol + 1);
                        col.put("counterparty", timeCol + 2);
                        // (timeCol+3 对方账号) 可选
                        col.put("product", timeCol + 4);
                        col.put("direction", timeCol + 5);
                        col.put("amount", timeCol + 6);
                        col.put("payMethod", timeCol + 7);
                        col.put("status", timeCol + 8);
                        col.put("orderId", timeCol + 9);
                        col.put("merchantOrderId", timeCol + 10);
                        col.put("remark", timeCol + 11);
                    }
                    throw new com.alibaba.excel.exception.ExcelAnalysisStopException();
                }
            }
            @Override public void doAfterAllAnalysed(AnalysisContext context) {}
        };

        try { EasyExcel.read(new ByteArrayInputStream(bytes), headerScan).sheet().doRead(); }
        catch (com.alibaba.excel.exception.ExcelAnalysisStopException ignore) {}
        if (headerRowIndex.get() == -1) throw new IllegalStateException("未找到表头行（支付宝）");

        int headRowNum = headerRowIndex.get() + 1;

        final List<WxPayTxn> out = new ArrayList<WxPayTxn>();
        AnalysisEventListener<Map<Integer, String>> dataReader = new AnalysisEventListener<Map<Integer, String>>() {
            @Override public void invoke(Map<Integer, String> row, AnalysisContext context) {
                try {
                    String timeStr = getCell(row, col.get("time"));
                    if (isBlank(timeStr)) return;
                    LocalDateTime tt = parseAliTimeFlexible(timeStr);

                    WxPayTxn t = new WxPayTxn();
                    t.setTradeTime(tt);
                    t.setTradeDate(tt.toLocalDate());
                    t.setTradeHour(tt.getHour());
                    t.setWeekday(tt.getDayOfWeek().getValue());

                    String tradeType = getCell(row, col.get("tradeType"));
                    t.setTradeType(isBlank(tradeType) ? "支付宝" : tradeType);

                    t.setCounterparty(getCell(row, col.get("counterparty")));
                    t.setProduct(getCell(row, col.get("product")));

                    String dirText = getCell(row, col.get("direction"));
                    String amountStr = getCell(row, col.get("amount"));
                    DirectionPair dp = toAliDirection(dirText, amountStr);
                    t.setDirection(dp.direction);
                    t.setAmount(dp.absAmount);

                    t.setPayMethod(getCell(row, col.get("payMethod")));
                    t.setStatus(getCell(row, col.get("status")));
                    t.setOrderId(stripQuotes(getCell(row, col.get("orderId"))));
                    t.setMerchantOrderId(stripQuotes(getCell(row, col.get("merchantOrderId"))));
                    t.setRemark(getCell(row, col.get("remark")));

                    t.setImportBatchId(batchId);
                    t.setCreatedAt(LocalDateTime.now());
                    out.add(t);
                } catch (Exception e) {
                    System.err.println("解析失败(支付宝-XLSX): row=" + row + " err=" + e.getMessage());
                }
            }
            @Override public void doAfterAllAnalysed(AnalysisContext context) {}
        };

        EasyExcel.read(new ByteArrayInputStream(bytes), dataReader).sheet().headRowNumber(headRowNum).doRead();
        return out;
    }

    // ======================= B. CSV(GBK/GB18030) 路径 =======================
    private List<WxPayTxn> parseAlipayCsv(byte[] bytes, Long batchId) throws IOException {
        // 1) 优先用 GB18030（GBK 的超集），如果识别度差再退回 UTF-8
        List<String[]> rows = readCsvWithCharset(bytes, "GB18030");
        if (rows.isEmpty() || looksMojibake(rows)) {
            rows = readCsvWithCharset(bytes, "UTF-8");
        }

        // 2) 找到第一条“可解析时间”的数据行，上一行视为表头
        int dataRow = -1, timeCol = 0;
        for (int r = 0; r < rows.size(); r++) {
            Integer tc = findTimeCol(rows.get(r));
            if (tc != null) { dataRow = r; timeCol = tc; break; }
        }
        if (dataRow == -1) throw new IllegalStateException("未找到数据行（CSV）");
        int headerRow = Math.max(0, dataRow - 1);
        String[] header = rows.get(headerRow);

        // 3) 通过表头名尝试映射；失败则按列数布局
        Map<String,Integer> col = mapColsByHeader(header);
        if (!col.containsKey("time")) col.put("time", timeCol);

        if (!col.containsKey("amount") || !col.containsKey("orderId")) {
            // 根据行长度做简/长版默认映射
            String[] sample = rows.get(dataRow);
            int mx = sample.length - 1;
            if (mx >= 7 && mx <= 8) { // 简版(8 列)：时间 对方 商品 方向 金额 方式 状态 交易单号
                putIfAbsent(col, "counterparty", timeCol + 1);
                putIfAbsent(col, "product", timeCol + 2);
                putIfAbsent(col, "direction", timeCol + 3);
                putIfAbsent(col, "amount", timeCol + 4);
                putIfAbsent(col, "payMethod", timeCol + 5);
                putIfAbsent(col, "status", timeCol + 6);
                putIfAbsent(col, "orderId", timeCol + 7);
            } else { // 长版（常见 12 列）
                putIfAbsent(col, "tradeType", timeCol + 1);
                putIfAbsent(col, "counterparty", timeCol + 2);
                putIfAbsent(col, "product", timeCol + 4);
                putIfAbsent(col, "direction", timeCol + 5);
                putIfAbsent(col, "amount", timeCol + 6);
                putIfAbsent(col, "payMethod", timeCol + 7);
                putIfAbsent(col, "status", timeCol + 8);
                putIfAbsent(col, "orderId", timeCol + 9);
                putIfAbsent(col, "merchantOrderId", timeCol + 10);
                putIfAbsent(col, "remark", timeCol + 11);
            }
        }

        // 4) 读取数据行
        List<WxPayTxn> out = new ArrayList<WxPayTxn>();
        for (int r = dataRow; r < rows.size(); r++) {
            String[] row = rows.get(r);
            try {
                String timeStr = getCell(row, col.get("time"));
                if (isBlank(timeStr)) continue;
                LocalDateTime tt = parseAliTimeFlexible(timeStr);

                WxPayTxn t = new WxPayTxn();
                t.setTradeTime(tt);
                t.setTradeDate(tt.toLocalDate());
                t.setTradeHour(tt.getHour());
                t.setWeekday(tt.getDayOfWeek().getValue());

                String tradeType = getCell(row, col.get("tradeType"));
                t.setTradeType(isBlank(tradeType) ? "支付宝" : tradeType);

                t.setCounterparty(getCell(row, col.get("counterparty")));
                t.setProduct(getCell(row, col.get("product")));

                String dirText = getCell(row, col.get("direction"));
                String amountStr = getCell(row, col.get("amount"));
                DirectionPair dp = toAliDirection(dirText, amountStr);
                t.setDirection(dp.direction);
                t.setAmount(dp.absAmount);

                t.setPayMethod(getCell(row, col.get("payMethod")));
                t.setStatus(getCell(row, col.get("status")));
                t.setOrderId(stripQuotes(getCell(row, col.get("orderId"))));
                t.setMerchantOrderId(stripQuotes(getCell(row, col.get("merchantOrderId"))));
                t.setRemark(getCell(row, col.get("remark")));

                t.setImportBatchId(batchId);
                t.setCreatedAt(LocalDateTime.now());
                out.add(t);
            } catch (Exception e) {
                System.err.println("解析失败(支付宝-CSV): line=" + r + " err=" + e.getMessage());
            }
        }
        return out;
    }

// ======================= 小工具（与上面两条路径共用，保持 Java 8） =======================

    // 判断是否 XLSX/XLS：Zip 头 "PK"
    private static boolean isXlsxOrXls(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 0x50 && bytes[1] == 0x4B;
    }

    // 从 EasyExcel 的 Map 行里找“能解析为时间”的列
    private static Integer findTimeCol(Map<Integer,String> row) {
        for (Map.Entry<Integer,String> e : row.entrySet()) {
            if (e.getValue() == null) continue;
            String v = e.getValue().trim();
            try { parseAliTimeFlexible(v); return e.getKey(); } catch (Exception ignore) {}
        }
        return null;
    }

    // 从 CSV 的 String[] 里找“能解析为时间”的列
    private static Integer findTimeCol(String[] row) {
        for (int i = 0; i < row.length; i++) {
            String v = row[i];
            if (v == null) continue;
            v = v.trim();
            try { parseAliTimeFlexible(v); return i; } catch (Exception ignore) {}
        }
        return null;
    }

    private static int maxKey(Map<Integer,String> m){
        int mx = -1;
        for (Integer k : m.keySet()) if (k != null && k > mx) mx = k;
        return mx;
    }

    private static String getCell(Map<Integer,String> row, Integer idx) {
        if (idx == null) return null;
        String v = row.get(idx);
        return v == null ? null : v.replace('\u00A0',' ').trim();
    }
    private static String getCell(String[] row, Integer idx) {
        if (idx == null) return null;
        if (idx < 0 || idx >= row.length) return null;
        String v = row[idx];
        return v == null ? null : v.replace('\u00A0',' ').trim();
    }
    private static String stripQuotes(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.replace("\"","").replace("\u200B","").replace("\uFEFF","").trim();
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    // 时间解析：覆盖常见格式
    private static final java.time.format.DateTimeFormatter[] ALI_FORMATS = new java.time.format.DateTimeFormatter[] {
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    };
    private static LocalDateTime parseAliTimeFlexible(String s) {
        String v = s.trim();
        for (java.time.format.DateTimeFormatter f : ALI_FORMATS) {
            try { return LocalDateTime.parse(v, f); } catch (Exception ignore) {}
        }
        throw new IllegalArgumentException("无法解析时间: " + s);
    }

    // 金额与方向（当“方向”列乱码/缺失时，按金额正负判断）
    private static java.math.BigDecimal parseAliAmount(String s) {
        if (isBlank(s)) return java.math.BigDecimal.ZERO;
        String v = s.replace(",", "").replace("¥","").replace("人民币","").trim();
        if (v.isEmpty()) return java.math.BigDecimal.ZERO;
        return new java.math.BigDecimal(v);
    }
    private static class DirectionPair {
        private final String direction;     // INCOME / EXPENSE / NEUTRAL
        private final java.math.BigDecimal absAmount;
        private DirectionPair(String d, java.math.BigDecimal a) { this.direction = d; this.absAmount = a; }
    }
    private static DirectionPair toAliDirection(String dirText, String amountStr) {
        java.math.BigDecimal raw = parseAliAmount(amountStr);
        // 文本优先；没有/乱码时按金额正负
        if (!isBlank(dirText)) {
            String d = dirText.replace(" ", "").trim();
            if (d.contains("收入") || d.contains("收款") || d.contains("入")) return new DirectionPair("INCOME", raw.abs());
            if (d.contains("支出") || d.contains("付款") || d.contains("支") || d.contains("付")) return new DirectionPair("EXPENSE", raw.abs());
            if (d.contains("中性") || d.contains("不计收支")) return new DirectionPair("NEUTRAL", raw.abs());
        }
        int sign = raw.signum();
        if (sign < 0) return new DirectionPair("EXPENSE", raw.abs());
        if (sign > 0) return new DirectionPair("INCOME", raw.abs());
        return new DirectionPair("NEUTRAL", raw.abs());
    }

    // ========= CSV 读取（GB18030 优先，支持 , 与 \t，支持引号转义；纯 Java 8，无第三方依赖） =========
    private static List<String[]> readCsvWithCharset(byte[] bytes, String charsetName) throws IOException {
        java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes), charsetName));
        List<String[]> rows = new ArrayList<String[]>();
        String line;
        // 先探测分隔符（逗号 or 制表），取出现次数多者
        char delim = ',';
        br.mark(1 << 20); // 1MB 标记
        int commaCnt = 0, tabCnt = 0, probe = 0;
        while ((line = br.readLine()) != null && probe < 50) {
            commaCnt += countChar(line, ',');
            tabCnt += countChar(line, '\t');
            probe++;
        }
        br.reset();
        if (tabCnt > commaCnt) delim = '\t';

        while ((line = br.readLine()) != null) {
            rows.add(splitCsvLine(line, delim));
        }
        return rows;
    }
    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
    // RFC4180 简易实现：支持引号内逗号/制表，双引号转义
    private static String[] splitCsvLine(String line, char delim) {
        List<String> out = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                else inQuotes = !inQuotes;
            } else if (ch == delim && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        out.add(sb.toString());
        // 去掉 BOM/零宽
        for (int i = 0; i < out.size(); i++) {
            String v = out.get(i);
            if (v != null) out.set(i, v.replace("\uFEFF","").replace("\u200B",""));
        }
        return out.toArray(new String[out.size()]);
    }
    // 判断是否“看起来仍然乱码”（极粗糙：头几行中文占位符过多）
    private static boolean looksMojibake(List<String[]> rows){
        int bad = 0, total = 0;
        for (int i = 0; i < Math.min(rows.size(), 10); i++) {
            String[] r = rows.get(i);
            for (String v : r) {
                if (v == null) continue;
                total++;
                if (v.contains("��")) bad++;
            }
        }
        return total > 0 && bad * 10 > total; // >10% 就认为不合格
    }

    // 表头名到列位的映射（适配长版/简版常见命名）
    private static Map<String,Integer> mapColsByHeader(String[] header){
        Map<String,Integer> col = new HashMap<String, Integer>();
        if (header == null) return col;
        Map<String,Integer> idx = new HashMap<String, Integer>();
        for (int i = 0; i < header.length; i++) {
            String h = header[i] == null ? null : header[i].replace('\u00A0',' ').replace('（','(').replace('）',')').trim();
            if (h != null && !h.isEmpty()) idx.put(h, i);
        }
        putIfPresent(col, "time", idx, new String[]{"交易时间","交易创建时间","时间"});
        putIfPresent(col, "tradeType", idx, new String[]{"交易分类","类型"});
        putIfPresent(col, "counterparty", idx, new String[]{"交易对方","对方","对方账号","对方账户"});
        putIfPresent(col, "product", idx, new String[]{"商品说明","商品名称","商品"});
        putIfPresent(col, "direction", idx, new String[]{"收/支","收支","方向"});
        putIfPresent(col, "amount", idx, new String[]{"金额","金额(元)","金额（元）"});
        putIfPresent(col, "payMethod", idx, new String[]{"支付方式","收/付款方式","收/付方式","方式"});
        putIfPresent(col, "status", idx, new String[]{"交易状态","状态"});
        putIfPresent(col, "orderId", idx, new String[]{"交易订单号","交易号","订单号","交易单号"});
        putIfPresent(col, "merchantOrderId", idx, new String[]{"商家订单号","商户订单号"});
        putIfPresent(col, "remark", idx, new String[]{"备注","备注信息"});
        return col;
    }
    private static void putIfPresent(Map<String,Integer> dst, String key, Map<String,Integer> src, String[] names) {
        for (String n : names) if (src.containsKey(n)) { dst.put(key, src.get(n)); return; }
    }
    private static void putIfAbsent(Map<String,Integer> dst, String key, int idx) {
        if (!dst.containsKey(key)) dst.put(key, idx);
    }

}
