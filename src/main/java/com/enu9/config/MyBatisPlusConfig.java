package com.enu9.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.H2); // 你现在是 H2
        pagination.setOverflow(true);     // 页码溢出时跳到最后一页（可选）
        pagination.setMaxLimit(1000L);    // 每页最大条数（可选）
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
