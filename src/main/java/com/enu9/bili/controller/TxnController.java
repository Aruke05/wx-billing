package com.enu9.bili.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enu9.bili.DO.WxPayTxn;
import com.enu9.bili.controller.VO.TxnQuery;
import com.enu9.bili.mapper.WxPayTxnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TxnController {
    private final WxPayTxnMapper mapper;

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
        return mapper.selectPage(page, w);
    }

}