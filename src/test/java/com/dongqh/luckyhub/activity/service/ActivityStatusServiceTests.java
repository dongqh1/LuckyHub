package com.dongqh.luckyhub.activity.service;

import com.dongqh.luckyhub.activity.mapper.MarketingActivityMapper;
import com.dongqh.luckyhub.activity.service.impl.ActivityStatusServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityStatusServiceTests {

    @Test
    void refreshesRunningBeforeEndedAndReturnsCounts() {
        MarketingActivityMapper mapper = mock(MarketingActivityMapper.class);
        when(mapper.promoteScheduledToRunning()).thenReturn(2);
        when(mapper.finishExpiredActivities()).thenReturn(3);
        ActivityStatusService service = new ActivityStatusServiceImpl(mapper);

        ActivityStatusRefreshResult result = service.refreshStatuses();

        assertThat(result.runningCount()).isEqualTo(2);
        assertThat(result.endedCount()).isEqualTo(3);
        verify(mapper).promoteScheduledToRunning();
        verify(mapper).finishExpiredActivities();
    }
}
