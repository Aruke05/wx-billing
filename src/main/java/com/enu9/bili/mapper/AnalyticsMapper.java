package com.enu9.bili.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsMapper {

    // 星期分布（仅收入）
    @Select("SELECT weekday, SUM(amount) AS total, COUNT(*) AS cnt " +
            "FROM wx_pay_txn WHERE direction='INCOME' " +
            "AND trade_time BETWEEN #{start} AND #{end} " +
            "GROUP BY weekday ORDER BY weekday")
    List<Map<String, Object>> sumByWeekday(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    // 自定义时段分布（仅收入）
    @Select("SELECT " +
            "CASE " +
            " WHEN trade_hour BETWEEN 0 AND 5 THEN '凌晨(0-5)' " +
            " WHEN trade_hour BETWEEN 6 AND 9 THEN '早晨(6-9)' " +
            " WHEN trade_hour BETWEEN 10 AND 11 THEN '上午(10-11)' " +
            " WHEN trade_hour BETWEEN 12 AND 13 THEN '中午(12-13)' " +
            " WHEN trade_hour BETWEEN 14 AND 17 THEN '下午(14-17)' " +
            " WHEN trade_hour BETWEEN 18 AND 21 THEN '晚上(18-21)' " +
            " ELSE '深夜(22-23)' END AS bucket, " +
            "SUM(amount) AS total, COUNT(*) AS cnt " +
            "FROM wx_pay_txn WHERE direction='INCOME' " +
            "AND trade_time BETWEEN #{start} AND #{end} " +
            "GROUP BY bucket")
    List<Map<String, Object>> sumByTimeBuckets(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    // 小时分布（仅收入）
    @Select("SELECT trade_hour AS \"hour\", SUM(amount) AS total, COUNT(*) AS cnt " +
            "FROM wx_pay_txn " +
            "WHERE direction='INCOME' AND trade_time BETWEEN #{start} AND #{end} " +
            "GROUP BY trade_hour ORDER BY trade_hour")
    List<Map<String, Object>> sumByHour(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

}