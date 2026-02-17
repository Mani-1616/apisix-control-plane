package com.apisix.controlplane.apisix.validation;

import com.apisix.controlplane.apisix.model.RouteSpec;
import com.networknt.schema.ValidationMessage;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Delegates {@link RouteSpec} validation to the {@link ApisixSchemaValidator}
 * which runs the JSON Schema check.
 */
public class RouteSpecValidator implements ConstraintValidator<ValidRouteSpec, RouteSpec> {

    private final ApisixSchemaValidator schemaValidator;

    public RouteSpecValidator(ApisixSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    @Override
    public boolean isValid(RouteSpec value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null handling is @NotNull's job
        }

        Set<ValidationMessage> errors = schemaValidator.validateRoute(value);
        if (errors.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        for (ValidationMessage msg : errors) {
            context.buildConstraintViolationWithTemplate(msg.getMessage())
                   .addConstraintViolation();
        }
        return false;
    }
}
