package com.dongqh.luckyhub.lottery.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dongqh.luckyhub.lottery.algorithm.DrawCandidate.Type.NO_WIN;
import static com.dongqh.luckyhub.lottery.algorithm.DrawCandidate.Type.PRIZE_CANDIDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightedDrawEngineTests {

    @Test
    void usesExactHalfOpenBoundariesAndIndependentNoWinInterval() {
        assertCandidate(selectAt(0, active(11, 101, 10), active(12, 102, 30)), 11, 101);
        assertCandidate(selectAt(9, active(11, 101, 10), active(12, 102, 30)), 11, 101);
        assertCandidate(selectAt(10, active(11, 101, 10), active(12, 102, 30)), 12, 102);
        assertCandidate(selectAt(39, active(11, 101, 10), active(12, 102, 30)), 12, 102);

        FixedRandomSource random = new FixedRandomSource(40);
        DrawCandidate result = new WeightedDrawEngineImpl(random)
                .select(List.of(active(11, 101, 10), active(12, 102, 30)), 60);

        assertThat(result.type()).isEqualTo(NO_WIN);
        assertThat(random.bound()).isEqualTo(100L);
    }

    @Test
    void zeroNoWinWeightLeavesTheLastPrizeBoundaryReachable() {
        FixedRandomSource random = new FixedRandomSource(39);

        DrawCandidate result = new WeightedDrawEngineImpl(random)
                .select(List.of(active(11, 101, 10), active(12, 102, 30)), 0);

        assertCandidate(result, 12, 102);
        assertThat(random.bound()).isEqualTo(40L);
    }

    @Test
    void disabledAndSoldOutIntervalsBecomeNoWinWithoutRedistribution() {
        List<PrizeWeightSnapshot> prizes = List.of(
                new PrizeWeightSnapshot(11, 101, 10, 5, false),
                new PrizeWeightSnapshot(12, 102, 30, 0, true),
                active(13, 103, 20)
        );

        assertThat(new WeightedDrawEngineImpl(new FixedRandomSource(0)).select(prizes, 40).type())
                .isEqualTo(NO_WIN);
        assertThat(new WeightedDrawEngineImpl(new FixedRandomSource(10)).select(prizes, 40).type())
                .isEqualTo(NO_WIN);
        assertCandidate(new WeightedDrawEngineImpl(new FixedRandomSource(40)).select(prizes, 40), 13, 103);
    }

    @Test
    void requiresValidPositiveTotalWeight() {
        WeightedDrawEngine engine = new WeightedDrawEngineImpl(new FixedRandomSource(0));

        assertThatThrownBy(() -> engine.select(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.select(List.of(active(11, 101, 10)), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.select(
                List.of(new PrizeWeightSnapshot(11, 101, 0, 1, true)), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accumulatesBeyondIntegerRangeAndRejectsLongOverflow() {
        FixedRandomSource random = new FixedRandomSource(3_000_000_000L);
        DrawCandidate result = new WeightedDrawEngineImpl(random).select(List.of(
                active(11, 101, 2_000_000_000L),
                active(12, 102, 2_000_000_000L)
        ), 0);

        assertCandidate(result, 12, 102);
        assertThat(random.bound()).isEqualTo(4_000_000_000L);

        assertThatThrownBy(() -> new WeightedDrawEngineImpl(new FixedRandomSource(0)).select(List.of(
                active(11, 101, Long.MAX_VALUE),
                active(12, 102, 1)
        ), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private DrawCandidate selectAt(long value, PrizeWeightSnapshot... prizes) {
        return new WeightedDrawEngineImpl(new FixedRandomSource(value))
                .select(List.of(prizes), 60);
    }

    private PrizeWeightSnapshot active(long activityPrizeId, long prizeId, long weight) {
        return new PrizeWeightSnapshot(activityPrizeId, prizeId, weight, 1, true);
    }

    private void assertCandidate(DrawCandidate candidate, long activityPrizeId, long prizeId) {
        assertThat(candidate.type()).isEqualTo(PRIZE_CANDIDATE);
        assertThat(candidate.activityPrizeId()).isEqualTo(activityPrizeId);
        assertThat(candidate.prizeId()).isEqualTo(prizeId);
    }

    private static final class FixedRandomSource implements DrawRandomSource {
        private final long value;
        private long bound;

        private FixedRandomSource(long value) {
            this.value = value;
        }

        @Override
        public long nextLong(long bound) {
            this.bound = bound;
            return value;
        }

        private long bound() {
            return bound;
        }
    }
}
