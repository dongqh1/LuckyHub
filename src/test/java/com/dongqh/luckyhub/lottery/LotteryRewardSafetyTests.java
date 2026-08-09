package com.dongqh.luckyhub.lottery;

import com.dongqh.luckyhub.lottery.enums.RewardQuarantineReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryRewardSafetyTests {
    @Test
    void quarantineReasonsFitTheSchemaAndExposeNoRequestPayload() {
        assertThat(RewardQuarantineReason.values()).allSatisfy(reason -> {
            assertThat(reason.name()).hasSizeLessThanOrEqualTo(50);
            assertThat(reason.name()).doesNotContain("PAYLOAD", "SECRET", "TOKEN");
        });
    }
}
