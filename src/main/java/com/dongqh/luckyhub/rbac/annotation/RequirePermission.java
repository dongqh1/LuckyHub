package com.dongqh.luckyhub.rbac.annotation;


import java.lang.annotation.*;

@Documented
@Target({
        ElementType.TYPE,
        ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value();
}
