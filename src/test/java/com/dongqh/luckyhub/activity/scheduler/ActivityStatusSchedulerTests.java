package com.dongqh.luckyhub.activity.scheduler;

import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.service.ActivityStatusService;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ActivityStatusSchedulerTests {

    @Test
    void delegatesRefreshToStatusService() {
        ActivityStatusService service = mock(ActivityStatusService.class);
        ActivityStatusScheduler scheduler = new ActivityStatusScheduler(service);

        scheduler.refreshActivityStatuses();

        verify(service).refreshStatuses();
    }

    @Test
    void mapperUsesDatabaseTimeAndPersistedStatuses() throws NoSuchMethodException {
        Method promote = MarketingActivityMapper.class.getMethod("promoteScheduledToRunning");
        Method finish = MarketingActivityMapper.class.getMethod("finishExpiredActivities");
        String promoteSql = String.join(" ", promote.getAnnotation(Update.class).value());
        String finishSql = String.join(" ", finish.getAnnotation(Update.class).value());

        assertThat(promoteSql).contains("NOW(3)", "SCHEDULED", "RUNNING");
        assertThat(finishSql).contains("NOW(3)", "SCHEDULED", "RUNNING", "ENDED");
    }
}
