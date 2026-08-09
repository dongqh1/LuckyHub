package com.dongqh.luckyhub.prize;

import com.dongqh.luckyhub.prize.dto.CreatePrizeCommand;
import com.dongqh.luckyhub.prize.entity.MarketingPrize;
import com.dongqh.luckyhub.prize.enums.PrizeLevel;
import com.dongqh.luckyhub.prize.enums.PrizeType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class PrizeDomainModelTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidCreateCommand() {
        CreatePrizeCommand command = new CreatePrizeCommand(
                " ",
                null,
                null,
                "x".repeat(501),
                "x".repeat(501),
                null,
                null
        );

        assertThat(validator.validate(command))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "prizeName",
                        "prizeType",
                        "prizeLevel",
                        "imageUrl",
                        "description",
                        "stackable",
                        "rewardDefinitionId"
                );
    }

    @Test
    void keepsStableEnumNames() {
        assertThat(PrizeType.values())
                .extracting(Enum::name)
                .containsExactly("COUPON", "POINTS", "MEMBERSHIP", "PHYSICAL", "DRAW_CHANCE");
        assertThat(PrizeLevel.values())
                .extracting(Enum::name)
                .containsExactly("FIRST", "SECOND", "THIRD", "CONSOLATION");
    }

    @Test
    void encapsulatesAuditFields() throws NoSuchFieldException {
        assertThat(Modifier.isPrivate(MarketingPrize.class.getDeclaredField("createdAt").getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(MarketingPrize.class.getDeclaredField("updatedAt").getModifiers())).isTrue();
    }
}
