package com.enu9.bili.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enu9.bili.DO.WxPayTxn;
import com.enu9.bili.controller.VO.TxnQuery;
import com.enu9.bili.mapper.WxPayTxnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TxnController {
    private final WxPayTxnMapper wxPayTxnMapper;

    @GetMapping
    public IPage<WxPayTxn> list(TxnQuery q) {
        // MyBatis-Plus 的 Page：current=第几页, size=每页大小, searchCount=true 自动查总数
        Page<WxPayTxn> page = new Page<>(q.getPage(), q.getSize(), true);

        QueryWrapper<WxPayTxn> w = new QueryWrapper<>();

        // 时间范围
        if (q.getStart() != null && q.getEnd() != null) {
            w.between("trade_time", q.getStart(), q.getEnd());
        } else if (q.getStart() != null) {
            w.ge("trade_time", q.getStart());
        } else if (q.getEnd() != null) {
            w.le("trade_time", q.getEnd());
        }
        if (StringUtils.hasText(q.getProduct())) {
            w.like("product", q.getProduct());
        }


        // 其他条件
        if (StringUtils.hasText(q.getTradeType()))   w.eq("trade_type", q.getTradeType());
        if (StringUtils.hasText(q.getDirection()))   w.eq("direction", q.getDirection());
        if (StringUtils.hasText(q.getStatus()))      w.eq("status", q.getStatus());
        if (StringUtils.hasText(q.getPayMethod()))   w.eq("pay_method", q.getPayMethod());
        if (StringUtils.hasText(q.getCounterparty()))w.like("counterparty", q.getCounterparty());
        if (q.getMinAmount() != null)                w.ge("amount", q.getMinAmount());
        if (q.getMaxAmount() != null)                w.le("amount", q.getMaxAmount());

        // 排序
        w.orderByDesc("trade_time");

        // selectPage 自动返回 records、total、size、current、pages
        return wxPayTxnMapper.selectPage(page, w);
    }

    @PostMapping("/delete")
    public Map<String, Object> deleteBatch(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<String> orderIds = (List<String>) req.get("orderIds");
        int deleted = wxPayTxnMapper.deleteBatchByOrderIds(orderIds);
        return Collections.singletonMap("deleted", deleted);
    }



    @PostMapping("/delete-by-filter")
    public Map<String, Object> deleteByFilter(@RequestBody TxnQuery query) {
        QueryWrapper<WxPayTxn> qw = new QueryWrapper<>();
        if (query.getStart() != null && query.getEnd() != null) {
            qw.between("trade_time", query.getStart(), query.getEnd());
        }
        if (query.getCounterparty() != null && !"".equals(query.getCounterparty())) {
            qw.like("counterparty", query.getCounterparty());
        }
        if (query.getDirection() != null && !"".equals(query.getDirection())) {
            qw.eq("direction", query.getDirection());
        }
        if (query.getTradeType() != null && !"".equals(query.getTradeType())) {
            qw.eq("trade_type", query.getTradeType());
        }
        if (query.getStatus() != null && !"".equals(query.getStatus())) {
            qw.eq("status", query.getStatus());
        }
        if (query.getPayMethod() != null && !"".equals(query.getPayMethod())) {
            qw.eq("pay_method", query.getPayMethod());
        }
        if (query.getMinAmount() != null) {
            qw.ge("amount", query.getMinAmount());
        }
        if (query.getMaxAmount() != null) {
            qw.le("amount", query.getMaxAmount());
        }
        if (StringUtils.hasText(query.getProduct())) {
            qw.like("product", query.getProduct());
        }
        int deleted = wxPayTxnMapper.delete(qw);
        return Collections.singletonMap("deleted", deleted);
    }
}
