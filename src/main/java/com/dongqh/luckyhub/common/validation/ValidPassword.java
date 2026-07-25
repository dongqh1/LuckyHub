package com.dongqh.luckyhub.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "密码必须为8～72个UTF-8字节";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
