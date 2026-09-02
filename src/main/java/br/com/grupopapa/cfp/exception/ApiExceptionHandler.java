package br.com.grupopapa.cfp.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiErro(OffsetDateTime timestamp, int status, String erro, String mensagem,
                          String caminho, List<CampoErro> campos) { }

    public record CampoErro(String campo, String mensagem) { }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErro> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<CampoErro> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(this::paraCampoErro).toList();
        return montar(HttpStatus.BAD_REQUEST, "Verifique os campos destacados.", req, campos);
    }

    private CampoErro paraCampoErro(FieldError fe) {
        return new CampoErro(fe.getField(), fe.getDefaultMessage());
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ApiErro> negocio(NegocioException ex, HttpServletRequest req) {
        return montar(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiErro> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return montar(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ApiErro> credenciais(Exception ex, HttpServletRequest req) {
        String msg = (ex instanceof DisabledException)
                ? "Usuario inativo. Procure o administrador."
                : "E-mail ou senha invalidos.";
        return montar(HttpStatus.UNAUTHORIZED, msg, req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErro> acesso(AccessDeniedException ex, HttpServletRequest req) {
        return montar(HttpStatus.FORBIDDEN, "Voce nao tem permissao para esta operacao.", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErro> generico(Exception ex, HttpServletRequest req) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("erro", ex.getClass().getSimpleName());
        log.put("mensagem", ex.getMessage());
        System.err.println("Erro nao tratado: " + log);
        ex.printStackTrace();
        return montar(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro inesperado ao processar a solicitacao.", req, null);
    }

    private ResponseEntity<ApiErro> montar(HttpStatus status, String mensagem,
                                           HttpServletRequest req, List<CampoErro> campos) {
        ApiErro corpo = new ApiErro(OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                mensagem, req.getRequestURI(), campos);
        return ResponseEntity.status(status).body(corpo);
    }
}
