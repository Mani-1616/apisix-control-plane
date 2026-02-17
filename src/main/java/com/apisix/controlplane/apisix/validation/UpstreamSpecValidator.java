package com.apisix.controlplane.apisix.validation;

import com.apisix.controlplane.apisix.model.UpstreamSpec;
import com.networknt.schema.ValidationMessage;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Delegates {@link UpstreamSpec} validation to the {@link ApisixSchemaValidator}
 * which runs the JSON Schema check.
 */
public class UpstreamSpecValidator implements ConstraintValidator<ValidUpstreamSpec, UpstreamSpec> {

    private final ApisixSchemaValidator schemaValidator;

    public UpstreamSpecValidator(ApisixSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    @Override
    public boolean isValid(UpstreamSpec value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Set<ValidationMessage> errors = schemaValidator.validateUpstream(value);
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
