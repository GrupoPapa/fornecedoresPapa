package br.com.grupopapa.cfp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CnpjValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cnpj {
    String message() default "CNPJ invalido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
