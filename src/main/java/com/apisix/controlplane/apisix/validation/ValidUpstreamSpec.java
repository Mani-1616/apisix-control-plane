package com.apisix.controlplane.apisix.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint that validates an {@code UpstreamSpec} against the
 * APISIX 3.14 JSON Schema.
 */
@Documented
@Constraint(validatedBy = UpstreamSpecValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUpstreamSpec {
    String message() default "Invalid APISIX upstream specification";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
