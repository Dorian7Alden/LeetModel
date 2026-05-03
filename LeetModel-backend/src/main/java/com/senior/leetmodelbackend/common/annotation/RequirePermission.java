package com.senior.leetmodelbackend.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    String[] value();

    MatchMode mode() default MatchMode.ALL;

    boolean selfAccess() default false;

    enum MatchMode {
        ALL, ANY
    }
}
