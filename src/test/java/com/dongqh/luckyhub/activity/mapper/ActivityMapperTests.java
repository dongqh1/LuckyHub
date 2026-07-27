package com.dongqh.luckyhub.activity.mapper;

import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ActivityMapperTests {

    private final MarketingActivityMapper mapper;

    @Autowired
    ActivityMapperTests(MarketingActivityMapper mapper) {
        this.mapper = mapper;
    }

    @Test
    void persistsScheduledTransitionsUsingDatabaseTime() {
        LocalDateTime now = LocalDateTime.now();
        MarketingActivity runningCandidate = activity(
                "待开始后运行",
                ActivityStatus.SCHEDULED,
                now.minusMinutes(1),
                now.plusHours(1)
        );
        MarketingActivity endedCandidate = activity(
                "停机期间结束",
                ActivityStatus.SCHEDULED,
                now.minusHours(2),
                now.minusHours(1)
        );
        mapper.insert(runningCandidate);
        mapper.insert(endedCandidate);

        assertThat(mapper.promoteScheduledToRunning()).isGreaterThanOrEqualTo(1);
        assertThat(mapper.finishExpiredActivities()).isGreaterThanOrEqualTo(1);

        assertThat(mapper.selectById(runningCandidate.getId()).getStatus())
                .isEqualTo(ActivityStatus.RUNNING);
        assertThat(mapper.selectById(endedCandidate.getId()).getStatus())
                .isEqualTo(ActivityStatus.ENDED);
    }

    private MarketingActivity activity(
            String name,
            ActivityStatus status,
            LocalDateTime start,
            LocalDateTime end
    ) {
        MarketingActivity activity = new MarketingActivity();
        activity.setActivityName(name);
        activity.setStatus(status);
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setDailyLimit(1);
        activity.setCreatedBy(1L);
        return activity;
    }
}
