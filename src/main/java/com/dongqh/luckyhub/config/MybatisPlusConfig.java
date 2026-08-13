package com.dongqh.luckyhub.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@MapperScan(basePackages = {
        "com.dongqh.luckyhub.rbac.mapper",
        "com.dongqh.luckyhub.prize.mapper",
        "com.dongqh.luckyhub.activity.mapper",
        "com.dongqh.luckyhub.lottery.mapper",
        "com.dongqh.luckyhub.inventory.mapper",
        "com.dongqh.luckyhub.benefit.mapper",
        "com.dongqh.luckyhub.catalog.mapper",
        "com.dongqh.luckyhub.reward.mapper",
        "com.dongqh.luckyhub.inventory.channel.mapper",
        "com.dongqh.luckyhub.points.mapper",
        "com.dongqh.luckyhub.coupon.mapper",
        "com.dongqh.luckyhub.membership.mapper",
        "com.dongqh.luckyhub.order.mapper",
        "com.dongqh.luckyhub.payment.mapper",
        "com.dongqh.luckyhub.fulfillment.mapper",
        "com.dongqh.luckyhub.drawchance.mapper",
        "com.dongqh.luckyhub.shipping.mapper"
})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
