package com.dongqh.luckyhub.coupon;

import com.dongqh.luckyhub.coupon.dto.*;
import com.dongqh.luckyhub.coupon.enums.*;
import com.dongqh.luckyhub.coupon.service.CouponService;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class CouponServiceTests {
    @Autowired CouponService service; @Autowired JdbcTemplate jdbc; @Autowired SysUserMapper users;
    private Long userId;

    @BeforeEach void setUp(){ jdbc.update("DELETE FROM coupon_issue_record"); jdbc.update("DELETE FROM user_coupon"); jdbc.update("DELETE FROM coupon_template"); SysUser u=new SysUser();u.setUsername("coupon-"+UUID.randomUUID());u.setPassword("x");u.setNickname("券用户");u.setStatus(1);users.insert(u);userId=u.getId(); }
    @AfterEach void tearDown(){ jdbc.update("DELETE FROM coupon_issue_record");jdbc.update("DELETE FROM user_coupon");jdbc.update("DELETE FROM coupon_template");users.deleteById(userId); }

    @Test void issuesIdempotentlyEnforcesLimitAndTransitionsSafely(){
        var template=service.createTemplate(new CreateCouponTemplateCommand("C-20","满100减20",CouponType.THRESHOLD,10000L,2000L,null,LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(30),1,true));
        var first=service.issue(new IssueCouponCommand("ISSUE-1","UC-1",template.id(),userId));
        var repeated=service.issue(new IssueCouponCommand("ISSUE-1","UC-1",template.id(),userId));
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThatThrownBy(()->service.issue(new IssueCouponCommand("ISSUE-2","UC-2",template.id(),userId))).hasMessageContaining("领取上限");
        var locked=service.lockForOrder(userId,first.id(),"ORDER-1",null,10000L,false);
        assertThat(locked.discountCent()).isEqualTo(2000L);
        assertThat(service.lockForOrder(userId,first.id(),"ORDER-1",null,10000L,false).userCouponId()).isEqualTo(first.id());
        service.releaseForOrder(first.id(),"ORDER-1");
        service.lockForOrder(userId,first.id(),"ORDER-2",null,10000L,false);
        service.useForOrder(first.id(),"ORDER-2");
        assertThat(service.getMine(userId,first.id()).status()).isEqualTo(UserCouponStatus.USED);
        assertThatThrownBy(()->service.releaseForOrder(first.id(),"ORDER-2")).hasMessageContaining("状态冲突");
    }

    @Test void rejectsThresholdProductAndMembershipStackingViolations(){
        var t=service.createTemplate(new CreateCouponTemplateCommand("P-10","指定商品券",CouponType.THRESHOLD,5000L,1000L,88L,LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(10),3,false));
        var c=service.issue(new IssueCouponCommand("IP-1","UPC-1",t.id(),userId));
        assertThatThrownBy(()->service.lockForOrder(userId,c.id(),"O-LOW",88L,4999L,false)).hasMessageContaining("不适用");
        assertThatThrownBy(()->service.lockForOrder(userId,c.id(),"O-PRODUCT",99L,6000L,false)).hasMessageContaining("不适用");
        assertThatThrownBy(()->service.lockForOrder(userId,c.id(),"O-MEMBER",88L,6000L,true)).hasMessageContaining("不适用");
    }
}
