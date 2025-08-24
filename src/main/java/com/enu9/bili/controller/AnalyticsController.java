package com.enu9.bili.controller;

import com.enu9.bili.mapper.AnalyticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsMapper mapper;

    @GetMapping("/summary")
    public Map<String,Object> summary(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String direction) {

        Map<String,Object> resp = new LinkedHashMap<>();
        List<Map<String,Object>> weekday = mapper.sumByWeekday(start, end, counterparty, direction);
        List<Map<String,Object>> buckets = mapper.sumByTimeBuckets(start, end, counterparty, direction);
        List<Map<String,Object>> hours   = mapper.sumByHour(start, end, counterparty, direction);
        resp.put("weekday", weekday);
        resp.put("timeBuckets", buckets);
        resp.put("hourly", hours);
        return resp;
    }

    @GetMapping("/weekday")
    public List<Map<String,Object>> weekday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String direction) {
        return mapper.sumByWeekday(start, end, counterparty, direction);
    }

    @GetMapping("/time-buckets")
    public List<Map<String, Object>> timeBuckets(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String direction) {
        return mapper.sumByTimeBuckets(start, end, counterparty, direction);
    }

    @GetMapping("/hourly")
    public List<Map<String, Object>> hourly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String direction) {
        return mapper.sumByHour(start, end, counterparty, direction);
    }


}