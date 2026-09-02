package br.com.grupopapa.cfp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

public class CodigoBarrasValidator implements ConstraintValidator<CodigoBarras, String> {

    /**
     * Quando false, valida apenas o tamanho do codigo (8, 12, 13 ou 14 digitos) e nao
     * confere o digito verificador. Util para testes ou para fornecedores que ainda nao
     * possuem GTIN oficial. Configuravel em app.validacao.gtin-digito-verificador.
     */
    @Value("${app.validacao.gtin-digito-verificador:true}")
    private boolean conferirDigitoVerificador = true;

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        if (valor == null || valor.isBlank()) {
            return true; // obrigatoriedade fica a cargo do @NotBlank
        }
        String gtin = valor.trim();

        if (!gtin.matches("\\d+")) {
            return recusar(context, "Informe somente numeros.");
        }
        if (!tamanhoValido(gtin)) {
            return recusar(context, "O codigo deve ter 8, 12, 13 ou 14 digitos (informados: "
                    + gtin.length() + ").");
        }
        if (conferirDigitoVerificador && !digitoConfere(gtin)) {
            return recusar(context, "O digito verificador nao confere - confira o numero impresso "
                    + "na embalagem. O ultimo digito deveria ser " + digitoEsperado(gtin) + ".");
        }
        return true;
    }

    private boolean recusar(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem).addConstraintViolation();
        return false;
    }

    public static boolean tamanhoValido(String gtin) {
        int t = gtin.length();
        return t == 8 || t == 12 || t == 13 || t == 14;
    }

    public static int digitoEsperado(String gtin) {
        int soma = 0;
        int peso = 3;
        for (int i = gtin.length() - 2; i >= 0; i--) {
            soma += Character.getNumericValue(gtin.charAt(i)) * peso;
            peso = (peso == 3) ? 1 : 3;
        }
        return (10 - (soma % 10)) % 10;
    }

    public static boolean digitoConfere(String gtin) {
        return digitoEsperado(gtin) == Character.getNumericValue(gtin.charAt(gtin.length() - 1));
    }

    /** Validacao completa (tamanho + digito), usada fora do Bean Validation. */
    public static boolean valido(String gtin) {
        return gtin != null && gtin.matches("\\d+") && tamanhoValido(gtin) && digitoConfere(gtin);
    }
}
