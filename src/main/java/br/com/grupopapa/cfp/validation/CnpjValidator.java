package br.com.grupopapa.cfp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CnpjValidator implements ConstraintValidator<Cnpj, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        if (valor == null || valor.isBlank()) return true;
        return valido(valor);
    }

    public static String somenteDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }

    public static boolean valido(String cnpj) {
        String d = somenteDigitos(cnpj);
        if (d == null || d.length() != 14 || d.chars().distinct().count() == 1) return false;
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digito(d, p1) == Character.getNumericValue(d.charAt(12))
            && digito(d, p2) == Character.getNumericValue(d.charAt(13));
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.getNumericValue(d.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
