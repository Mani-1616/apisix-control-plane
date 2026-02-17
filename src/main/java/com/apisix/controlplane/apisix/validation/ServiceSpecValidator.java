package com.apisix.controlplane.apisix.validation;

import com.apisix.controlplane.apisix.model.ServiceSpec;
import com.networknt.schema.ValidationMessage;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Delegates {@link ServiceSpec} validation to the {@link ApisixSchemaValidator}
 * which runs the JSON Schema check.
 */
public class ServiceSpecValidator implements ConstraintValidator<ValidServiceSpec, ServiceSpec> {

    private final ApisixSchemaValidator schemaValidator;

    public ServiceSpecValidator(ApisixSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    @Override
    public boolean isValid(ServiceSpec value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Set<ValidationMessage> errors = schemaValidator.validateService(value);
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
