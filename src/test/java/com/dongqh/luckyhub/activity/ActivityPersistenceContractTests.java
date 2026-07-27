package com.dongqh.luckyhub.activity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.activity.entity.MarketingActivity;
import com.dongqh.luckyhub.activity.entity.MarketingActivityPrize;
import com.dongqh.luckyhub.activity.enums.ActivityStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityPersistenceContractTests {

    @Test
    void definesPersistedStatusesInLifecycleOrder() {
        assertThat(ActivityStatus.values()).containsExactly(
                ActivityStatus.DRAFT,
                ActivityStatus.SCHEDULED,
                ActivityStatus.RUNNING,
                ActivityStatus.ENDED,
                ActivityStatus.DISABLED
        );
    }

    @Test
    void mapsExistingActivityTables() {
        assertThat(MarketingActivity.class.getAnnotation(TableName.class).value())
                .isEqualTo("marketing_activity");
        assertThat(MarketingActivityPrize.class.getAnnotation(TableName.class).value())
                .isEqualTo("marketing_activity_prize");
    }
}
