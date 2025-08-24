package com.enu9;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 微信支付账单分析 应用启动类
 */
@SpringBootApplication
@MapperScan("com.enu9.*")
// 解释：
// - com.enu9..mapper 表示 com.enu9 包下的任意子包里只要叫 mapper 的都会被扫描
// - 也可以写成 @MapperScan("com.enu9")，这样就会递归扫描 com.enu9 下所有包的 @Mapper 接口
public class WxBillApplication {
    public static void main(String[] args) {
        SpringApplication.run(WxBillApplication.class, args);
    }
}
