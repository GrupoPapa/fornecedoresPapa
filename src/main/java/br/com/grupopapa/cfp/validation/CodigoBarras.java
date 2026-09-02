package br.com.grupopapa.cfp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Valida codigo de barras GTIN-8/12/13/14 (tamanho + digito verificador). */
@Documented
@Constraint(validatedBy = CodigoBarrasValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodigoBarras {
    String message() default "Codigo de barras invalido (informe um GTIN de 8, 12, 13 ou 14 digitos)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
