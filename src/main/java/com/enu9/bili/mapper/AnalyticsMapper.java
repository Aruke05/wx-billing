package com.enu9.bili.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsMapper {

    List<Map<String, Object>> sumByWeekday(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("counterparty") String counterparty,
                                           @Param("direction") String direction,
                                           @Param("product") String product,
                                           @Param("channelType") String channelType,
                                           @Param("minAmount") BigDecimal minAmount,
                                           @Param("maxAmount") BigDecimal maxAmount);

    List<Map<String, Object>> sumByTimeBuckets(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               @Param("counterparty") String counterparty,
                                               @Param("direction") String direction,
                                               @Param("product") String product,
                                               @Param("channelType") String channelType,
                                               @Param("minAmount") BigDecimal minAmount,
                                               @Param("maxAmount") BigDecimal maxAmount);

    List<Map<String, Object>> sumByHour(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("counterparty") String counterparty,
                                        @Param("direction") String direction,
                                        @Param("product") String product,
                                        @Param("channelType") String channelType,
                                        @Param("minAmount") BigDecimal minAmount,
                                        @Param("maxAmount") BigDecimal maxAmount);

}

