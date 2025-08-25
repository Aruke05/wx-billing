package com.enu9.bili.controller;

import com.enu9.bili.controller.VO.AnalyticsQuery;
import com.enu9.bili.mapper.AnalyticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsMapper mapper;

    @GetMapping("/summary")
    public Map<String,Object> summary(AnalyticsQuery q) {
        Map<String,Object> resp = new LinkedHashMap<>();
        List<Map<String,Object>> weekday = mapper.sumByWeekday(q.getStart(), q.getEnd(),
                q.getCounterparty(), q.getDirection(), q.getMinAmount(), q.getMaxAmount());
        List<Map<String,Object>> buckets = mapper.sumByTimeBuckets(q.getStart(), q.getEnd(),
                q.getCounterparty(), q.getDirection(), q.getMinAmount(), q.getMaxAmount());
        List<Map<String,Object>> hours   = mapper.sumByHour(q.getStart(), q.getEnd(),
                q.getCounterparty(), q.getDirection(), q.getMinAmount(), q.getMaxAmount());
        resp.put("weekday", weekday);
        resp.put("timeBuckets", buckets);
        resp.put("hourly", hours);
        return resp;
    }

    @GetMapping("/weekday")
    public List<Map<String,Object>> weekday(AnalyticsQuery q) {
        return mapper.sumByWeekday(q.getStart(), q.getEnd(),
                q.getCounterparty(), q.getDirection(), q.getMinAmount(), q.getMaxAmount());
    }

    @GetMapping("/time-buckets")
    public List<Map<String, Object>> timeBuckets(AnalyticsQuery q) {
        return mapper.sumByTimeBuckets(q.getStart(), q.getEnd(),
                q.getCounterparty(), q.getDirection(), q.getMinAmount(), q.getMaxAmount());
    }

    @GetMapping("/hourly")
    public List<Map<String, Object>> hourly(AnalyticsQuery q) {
        return mapper.sumByHour(q.getStart(), q.getEnd(),
                q.getCounterparty(), q.getDirection(), q.getMinAmount(), q.getMaxAmount());
    }
}
