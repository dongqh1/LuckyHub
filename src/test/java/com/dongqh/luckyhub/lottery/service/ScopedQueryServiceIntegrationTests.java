package com.dongqh.luckyhub.lottery.service;

import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.benefit.dto.BenefitQuery;
import com.dongqh.luckyhub.benefit.mapper.UserBenefitMapper;
import com.dongqh.luckyhub.benefit.service.BenefitQueryServiceImpl;
import com.dongqh.luckyhub.common.exception.ForbiddenException;
import com.dongqh.luckyhub.lottery.dto.DrawOrderQuery;
import com.dongqh.luckyhub.lottery.dto.DrawRecordQuery;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawOrderMapper;
import com.dongqh.luckyhub.lottery.mapper.LotteryDrawRecordMapper;
import com.dongqh.luckyhub.lottery.service.impl.LotteryQueryServiceImpl;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import com.dongqh.luckyhub.rbac.service.DataScopeService;
import com.dongqh.luckyhub.rbac.service.UserDataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class ScopedQueryServiceIntegrationTests {
    private static final long SELF = 880001L;
    private static final long OTHER = 880002L;
    @Autowired LotteryDrawOrderMapper orderMapper;
    @Autowired LotteryDrawRecordMapper recordMapper;
    @Autowired UserBenefitMapper benefitMapper;
    @Autowired MarketingActivityMapper activityMapper;
    @Autowired JdbcTemplate jdbc;
    private DataScopeService dataScope;
    private LotteryQueryService lotteryQueries;
    private com.dongqh.luckyhub.benefit.service.BenefitQueryService benefitQueries;
    private String marker;

    @BeforeEach
    void setUp() {
        marker = "task14-" + UUID.randomUUID();
        dataScope = mock(DataScopeService.class);
        lotteryQueries = new LotteryQueryServiceImpl(activityMapper, orderMapper, recordMapper, benefitMapper, dataScope);
        benefitQueries = new BenefitQueryServiceImpl(benefitMapper, recordMapper, dataScope);
        LoginContext.set(new LoginPrincipal(SELF, "task14", "session"));
        seed(SELF, marker + "-self", 1);
        seed(OTHER, marker + "-other", 2);
    }

    @AfterEach
    void cleanUp() {
        LoginContext.clear();
        jdbc.update("DELETE b FROM user_benefit b JOIN lottery_draw_record r ON r.id=b.draw_record_id WHERE r.request_id LIKE ?", marker + "%");
        jdbc.update("DELETE FROM lottery_draw_record WHERE request_id LIKE ?", marker + "%");
        jdbc.update("DELETE FROM lottery_draw_order WHERE request_id LIKE ?", marker + "%");
    }

    @Test
    void recordAndBenefitPagesUseDatabaseScopeAndHistoricalSnapshotWithoutNPlusOne() {
        when(dataScope.resolveUserScope(null, PermissionCodes.LOTTERY_RECORD_READ_ALL)).thenReturn(UserDataScope.one(SELF));
        when(dataScope.resolveUserScope(null, PermissionCodes.BENEFIT_READ_ALL)).thenReturn(UserDataScope.one(SELF));

        var records = lotteryQueries.pageRecords(new DrawRecordQuery());
        var benefits = benefitQueries.page(new BenefitQuery());

        assertThat(records.records()).hasSize(1);
        assertThat(records.records().get(0).userId()).isEqualTo(SELF);
        assertThat(records.records().get(0).prizeName()).isEqualTo("历史奖品-1");
        assertThat(records.records().get(0).benefitId()).isNotNull();
        assertThat(benefits.records()).hasSize(1);
        assertThat(benefits.records().get(0).userId()).isEqualTo(SELF);
        assertThat(benefits.records().get(0).prizeName()).isEqualTo("历史奖品-1");
    }

    @Test
    void drawLookupChecksOwnershipAndOrderPageIsStableAndScoped() {
        seed(SELF, marker + "-self-new", 3);
        when(dataScope.resolveUserScope(SELF, PermissionCodes.LOTTERY_DRAW_READ_ALL)).thenReturn(UserDataScope.one(SELF));
        when(dataScope.resolveUserScope(null, PermissionCodes.LOTTERY_ORDER_READ_ALL)).thenReturn(UserDataScope.one(SELF));

        assertThat(lotteryQueries.getDraw(marker + "-self").requestId()).isEqualTo(marker + "-self");
        assertThat(lotteryQueries.pageOrders(new DrawOrderQuery()).records())
                .extracting(view -> view.requestId()).containsExactly(marker + "-self-new", marker + "-self");
    }

    @Test
    void explicitlyRequestingForeignUserIsRejectedRatherThanSilentlyRewritten() {
        DrawRecordQuery query = new DrawRecordQuery();
        query.setUserId(OTHER);
        when(dataScope.resolveUserScope(OTHER, PermissionCodes.LOTTERY_RECORD_READ_ALL))
                .thenThrow(new ForbiddenException("无权查询其他用户的数据"));
        assertThatThrownBy(() -> lotteryQueries.pageRecords(query)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void foreignDrawAndBenefitDetailsAreProtectedAgainstIdor() {
        when(dataScope.resolveUserScope(OTHER, PermissionCodes.LOTTERY_DRAW_READ_ALL))
                .thenThrow(new ForbiddenException("无权查询其他用户的数据"));
        when(dataScope.resolveUserScope(OTHER, PermissionCodes.BENEFIT_READ_ALL))
                .thenThrow(new ForbiddenException("无权查询其他用户的数据"));
        Long foreignBenefitId = jdbc.queryForObject("SELECT b.id FROM user_benefit b JOIN lottery_draw_record r ON r.id=b.draw_record_id WHERE r.request_id=?",
                Long.class, marker + "-other");

        assertThatThrownBy(() -> lotteryQueries.getDraw(marker + "-other")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> benefitQueries.getById(foreignBenefitId)).isInstanceOf(ForbiddenException.class);
    }

    private void seed(long userId, String requestId, int suffix) {
        jdbc.update("INSERT INTO lottery_draw_order(request_id,user_id,activity_id,draw_count,draw_date,status,completed_at) VALUES(?,?,991001,1,CURRENT_DATE,'SUCCESS',NOW(3))",
                requestId, userId);
        Long orderId = jdbc.queryForObject("SELECT id FROM lottery_draw_order WHERE request_id=?", Long.class, requestId);
        jdbc.update("INSERT INTO lottery_draw_record(order_id,request_id,sequence_no,user_id,activity_id,result_type,prize_id,prize_name,prize_type,prize_image_url,draw_time) VALUES(?,?,1,?,991001,'WIN',?,?,'COUPON',?,NOW(3))",
                orderId, requestId, userId, 992000 + suffix, "历史奖品-" + suffix, "https://cdn/" + suffix + ".png");
        Long recordId = jdbc.queryForObject("SELECT id FROM lottery_draw_record WHERE request_id=?", Long.class, requestId);
        jdbc.update("INSERT INTO user_benefit(draw_record_id,user_id,prize_id,prize_type,quantity,status,obtained_at) VALUES(?,?,?,'COUPON',1,'AVAILABLE',NOW(3))",
                recordId, userId, 992000 + suffix);
    }
}
