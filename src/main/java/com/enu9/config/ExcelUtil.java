// AliDict.java
package com.enu9.config;

import com.alibaba.excel.util.StringUtils;
import org.apache.poi.ss.formula.functions.T;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 支付宝字典/解析工具 */
public class ExcelUtil {

    // --------------- 工具方法 ---------------
    public static byte[] toBytes(InputStream in) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(16 * 1024)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    public static boolean contains(Map<Integer, String> row, String target) {
        for (String v : row.values()) {
            String n = norm(v);
            if (target.equals(n)) return true;
        }
        return false;
    }

    public static String norm(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').trim();
    }

    public static String trim(String s) { return s == null ? null : s.trim(); }
    public static String trimToNull(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }

    public static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    public static String get(Map<Integer, String> row, Integer idx) {
        if (idx == null) return null;
        String v = row.get(idx);
        return v == null ? null : v.trim();
    }

    public static <T> List<List<T>> chunks(List<T> list, int size) {
        List<List<T>> res = new ArrayList<List<T>>();
        for (int i = 0; i < list.size(); i += size) {
            res.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return res;
    }

    // ---- 支付宝：时间/金额/方向解析（Java 8） ----
    public static final DateTimeFormatter ALI_TF1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter ALI_TF2 = DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss");

    public static LocalDateTime parseAliTime(String s) {
        String v = s.trim();
        try { return LocalDateTime.parse(v, ALI_TF1); } catch (Exception ignore) {}
        return LocalDateTime.parse(v, ALI_TF2);
    }

    public static class DirectionPair {
        public final String direction;     // INCOME / EXPENSE / NEUTRAL
        public final BigDecimal absAmount; // 始终为正
        public DirectionPair(String d, BigDecimal a) { this.direction = d; this.absAmount = a; }
    }

    public static DirectionPair toAliDirection(String dirText, String amountStr) {
        BigDecimal raw = parseAliAmount(amountStr);
        String direction = "NEUTRAL";
        if (dirText != null) {
            String d = dirText.trim();
            if (d.contains("支出")) direction = "EXPENSE";
            else if (d.contains("收入")) direction = "INCOME";
        } else {
            int sign = raw.signum();
            if (sign < 0) direction = "EXPENSE";
            else if (sign > 0) direction = "INCOME";
        }
        return new DirectionPair(direction, raw.abs());
    }

    // 表头规整：去NBSP、全角括号->半角括号、去空白
    public static String normHeader(String s) {
        if (s == null) return null;
        String v = s.replace('\u00A0',' ')
                .replace('（','(')
                .replace('）',')')
                .trim();
        return v.isEmpty() ? null : v;
    }

    public static boolean hasAny(Map<String,Integer> found, String[] names) {
        for (String n : names) if (found.containsKey(n)) return true;
        return false;
    }

    // putIfPresent 的重载：支持数组别名
    public static void putIfPresent(Map<String,Integer> dst, String key, Map<String,Integer> src, String[] names) {
        for (String n : names) {
            if (src.containsKey(n)) { dst.put(key, src.get(n)); return; }
        }
    }

    public static String stripQuotes(String s) {
        if (s == null) return null;
        String v = s.trim();
        // 有的导出里外面带英文引号或包含零宽字符
        v = v.replace("\"","").replace("\u200B","").replace("\uFEFF","");
        return v.trim();
    }

    public static String getClean(Map<Integer, String> row, Integer idx) {
        if (idx == null) return null;
        String v = row.get(idx);
        if (v == null) return null;
        // 统一清洗：去NBSP、去引号、trim
        v = v.replace('\u00A0',' ');
        v = stripQuotes(v);
        return v.isEmpty() ? null : v;
    }

    // 时间：支持有秒/无秒、短月日
    public static final DateTimeFormatter[] ALI_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    };

    public static LocalDateTime parseAliTimeFlexible(String s) {
        String v = s.trim();
        for (DateTimeFormatter f : ALI_FORMATS) {
            try { return LocalDateTime.parse(v, f); } catch (Exception ignore) {}
        }
        // 实在不行再抛
        throw new IllegalArgumentException("无法解析时间: " + s);
    }

    public static BigDecimal parseAliAmount(String s) {
        if (StringUtils.isBlank(s)) return BigDecimal.ZERO;
        String v = s.replace(",", "")
                .replace("¥", "")
                .replace("人民币", "")
                .trim();
        if (v.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(v);
    }

}
