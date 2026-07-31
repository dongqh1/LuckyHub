package com.dongqh.luckyhub.lottery.algorithm;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class WeightedDrawEngineImpl implements WeightedDrawEngine {

    private final DrawRandomSource randomSource;

    public WeightedDrawEngineImpl(DrawRandomSource randomSource) {
        this.randomSource = randomSource;
    }

    @Override
    public DrawCandidate select(List<PrizeWeightSnapshot> prizes, int noWinWeight) {
        Objects.requireNonNull(prizes, "prizes must not be null");
        if (noWinWeight < 0) {
            throw new IllegalArgumentException("noWinWeight must not be negative");
        }

        long totalWeight = noWinWeight;
        for (PrizeWeightSnapshot prize : prizes) {
            validate(prize);
            try {
                totalWeight = Math.addExact(totalWeight, prize.weight());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("total weight overflows long", exception);
            }
        }
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("total weight must be positive");
        }

        long selected = randomSource.nextLong(totalWeight);
        if (selected < 0 || selected >= totalWeight) {
            throw new IllegalStateException("random source returned a value outside its bound");
        }

        long upperExclusive = 0;
        for (PrizeWeightSnapshot prize : prizes) {
            upperExclusive = Math.addExact(upperExclusive, prize.weight());
            if (selected < upperExclusive) {
                if (!prize.enabled() || prize.remainingStock() == 0) {
                    return DrawCandidate.noWin();
                }
                return DrawCandidate.prize(prize.activityPrizeId(), prize.prizeId());
            }
        }
        return DrawCandidate.noWin();
    }

    private void validate(PrizeWeightSnapshot prize) {
        if (prize == null) {
            throw new IllegalArgumentException("prize must not be null");
        }
        if (prize.weight() <= 0) {
            throw new IllegalArgumentException("prize weight must be positive");
        }
        if (prize.remainingStock() < 0) {
            throw new IllegalArgumentException("remaining stock must not be negative");
        }
    }
}
