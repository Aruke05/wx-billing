package com.enu9.bili.DO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class WxDict {
    private WxDict() {}

    public static String toDirection(String s) {
        if (s == null) return "NEUTRAL";
        s = s.trim();
        if (s.contains("收入")) return "INCOME";
        if (s.contains("支出")) return "EXPENSE";
        return "NEUTRAL";
    }

    public static BigDecimal parseAmount(String s) {
        // 兼容 "¥200.00" / "200" / "200.00 元"
        if (s == null) return BigDecimal.ZERO;
        String cleaned = s.replace("¥","").replace("元","").replace(",","").trim();
        return new BigDecimal(cleaned);
    }

    public static LocalDateTime parseTime(String s) {
        // 微信导出常见格式: 2025-08-23 23:36:21
        return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
