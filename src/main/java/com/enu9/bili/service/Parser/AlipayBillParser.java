// package: com.enu9.bili.service.parser
package com.enu9.bili.service.Parser;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.enu9.bili.DO.payTxn;
import com.enu9.bili.service.Parser.BillParser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.enu9.config.ExcelUtil.*;

@Component
public class AlipayBillParser implements BillParser {

    @Override
    public Channel channel() { return Channel.ALIPAY; }

    @Override
    public List<payTxn> parse(byte[] bytes, Long batchId) throws IOException {
        if (bytes == null || bytes.length == 0) return Collections.emptyList();
        // XLSX/XLS（ZIP 头 PK） 走 EasyExcel；否则按 CSV(GB18030/UTF-8) 解析
        return isXlsxOrXls(bytes) ? parseXlsx(bytes, batchId) : parseCsv(bytes, batchId);
    }

    // ------ A) XLS/XLSX：不依赖中文表头；遇到第一条可解析时间行，上一行视为表头 ------
    private List<payTxn> parseXlsx(byte[] bytes, Long batchId) throws IOException {
        final AtomicInteger headerRowIndex = new AtomicInteger(-1);
        final Map<String, Integer> col = new HashMap<String, Integer>();

        AnalysisEventListener<Map<Integer, String>> headerScan = new AnalysisEventListener<Map<Integer, String>>() {
            @Override public void invoke(Map<Integer, String> row, AnalysisContext context) {
                int r = context.readRowHolder().getRowIndex();
                Integer timeCol = findTimeCol(row);
                if (timeCol != null) {
                    headerRowIndex.set(Math.max(0, r - 1));
                    int mx = maxKey(row);
                    if (mx >= 7 && mx <= 8) { // 简版 8列：时间 对方 商品 方向 金额 方式 状态 交易单号
                        col.put("time", timeCol);
                        col.put("counterparty", timeCol + 1);
                        col.put("product", timeCol + 2);
                        col.put("direction", timeCol + 3);
                        col.put("amount", timeCol + 4);
                        col.put("payMethod", timeCol + 5);
                        col.put("status", timeCol + 6);
                        col.put("orderId", timeCol + 7);
                    } else { // 长版常见 12列
                        col.put("time", timeCol);
                        col.put("tradeType", timeCol + 1);
                        col.put("counterparty", timeCol + 2);
                        col.put("product", timeCol + 4);
                        col.put("direction", timeCol + 5);
                        col.put("amount", timeCol + 6);
                        col.put("payMethod", timeCol + 7);
                        col.put("status", timeCol + 8);
                        col.put("orderId", timeCol + 9);
                        col.put("merchantOrderId", timeCol + 10);
                        col.put("remark", timeCol + 11);
                    }
                    throw new ExcelAnalysisStopException();
                }
            }
            @Override public void doAfterAllAnalysed(AnalysisContext context) {}
        };

        try { EasyExcel.read(new ByteArrayInputStream(bytes), headerScan).sheet().doRead(); }
        catch (ExcelAnalysisStopException ignore) {}
        if (headerRowIndex.get() == -1) throw new IllegalStateException("未找到表头行（支付宝）");

        int headRowNum = headerRowIndex.get() + 1;
        final List<payTxn> out = new ArrayList<payTxn>();

        AnalysisEventListener<Map<Integer, String>> dataReader = new AnalysisEventListener<Map<Integer, String>>() {
            @Override public void invoke(Map<Integer, String> row, AnalysisContext context) {
                try {
                    String timeStr = getCell(row, col.get("time"));
                    if (isBlank(timeStr)) return;
                    LocalDateTime tt = parseAliTimeFlexible(timeStr);

                    payTxn t = new payTxn();
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

                    t.setChannelType(Channel.ALIPAY.name());
                    out.add(t);
                } catch (Exception e) {
                    System.err.println("解析失败(支付宝-XLSX): row=" + row + " err=" + e.getMessage());
                }
            }
            @Override public void doAfterAllAnalysed(AnalysisContext context) {}
        };

        EasyExcel.read(new ByteArrayInputStream(bytes), dataReader)
                .sheet().headRowNumber(headRowNum).doRead();
        return out;
    }

    // ------ B) CSV：优先 GB18030，失败回退 UTF-8；支持 , 与 \t 分隔 ------
    private List<payTxn> parseCsv(byte[] bytes, Long batchId) throws IOException {
        List<String[]> rows = readCsvWithCharset(bytes, "GB18030");
        if (rows.isEmpty() || looksMojibake(rows)) rows = readCsvWithCharset(bytes, "UTF-8");

        // 第一条“可解析时间”的行视为数据首行；其上一行是表头
        int dataRow = -1, timeCol = 0;
        for (int r = 0; r < rows.size(); r++) {
            Integer tc = findTimeCol(rows.get(r));
            if (tc != null) { dataRow = r; timeCol = tc; break; }
        }
        if (dataRow == -1) throw new IllegalStateException("未找到数据行（CSV）");
        int headerRow = Math.max(0, dataRow - 1);

        Map<String,Integer> col = mapColsByHeader(rows.get(headerRow));
        if (!col.containsKey("time")) col.put("time", timeCol);

        // 如果头映射不完整，按列数兜底（简版/长版）
        String[] sample = rows.get(dataRow);
        int mx = sample.length - 1;
        if (!col.containsKey("amount") || !col.containsKey("orderId")) {
            if (mx >= 7 && mx <= 8) {
                putIfAbsent(col, "counterparty", timeCol + 1);
                putIfAbsent(col, "product", timeCol + 2);
                putIfAbsent(col, "direction", timeCol + 3);
                putIfAbsent(col, "amount", timeCol + 4);
                putIfAbsent(col, "payMethod", timeCol + 5);
                putIfAbsent(col, "status", timeCol + 6);
                putIfAbsent(col, "orderId", timeCol + 7);
            } else {
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

        List<payTxn> out = new ArrayList<>();
        for (int r = dataRow; r < rows.size(); r++) {
            String[] row = rows.get(r);
            try {
                String timeStr = getCell(row, col.get("time"));
                if (isBlank(timeStr)) continue;
                LocalDateTime tt = parseAliTimeFlexible(timeStr);

                payTxn t = new payTxn();
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

                t.setChannelType(Channel.ALIPAY.name());
                out.add(t);
            } catch (Exception e) {
                System.err.println("解析失败(支付宝-CSV): line=" + r + " err=" + e.getMessage());
            }
        }
        return out;
    }

    // ====== 下方为工具：尽量与现有 ImportService 中实现保持一致（已略去注释） ======
    private static boolean isXlsxOrXls(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 0x50 && bytes[1] == 0x4B;
    }
    private static Integer findTimeCol(Map<Integer,String> row) {
        for (Map.Entry<Integer,String> e : row.entrySet()) {
            if (e.getValue() == null) continue;
            String v = e.getValue().trim();
            try { parseAliTimeFlexible(v); return e.getKey(); } catch (Exception ignore) {}
        }
        return null;
    }
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
        int mx = -1; for (Integer k : m.keySet()) if (k != null && k > mx) mx = k; return mx;
    }
    private static String getCell(Map<Integer,String> row, Integer idx) {
        if (idx == null) return null; String v = row.get(idx);
        return v == null ? null : v.replace('\u00A0',' ').trim();
    }
    private static String getCell(String[] row, Integer idx) {
        if (idx == null || idx < 0 || idx >= row.length) return null;
        String v = row[idx]; return v == null ? null : v.replace('\u00A0',' ').trim();
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
