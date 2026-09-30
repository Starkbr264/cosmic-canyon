package br.com.tradewind.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erros no padrão RFC 9457 (Problem Details), que e o que o Swagger UI e o
 * Postman esperam para exibir falhas de forma legivel.
 *
 * <p>A ordem e explicita de proposito: o {@code ProblemDetailsExceptionHandler}
 * do proprio Spring Boot (ativado por {@code spring.mvc.problemdetails.enabled})
 * tem precedencia alta e interceptaria {@code MethodArgumentNotValidException},
 * devolvendo {@code title: "Bad Request"} em vez do detalhe por campo. Aqui
 * precisamos ganhar dele.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso nao encontrado: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso nao encontrado");
        problem.setType(URI.create("https://api.tradewind.exemplo.com/errors/not-found"));
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        log.warn("Conflito: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflito de recurso");
        problem.setType(URI.create("https://api.tradewind.exemplo.com/errors/conflict"));
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Regra de negocio impedindo a operacao");
        problem.setType(URI.create("https://api.tradewind.exemplo.com/errors/business-rule"));
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos invalidos");
        problem.setTitle("Validacao falhou");
        problem.setType(URI.create("https://api.tradewind.exemplo.com/errors/validation"));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("fields", fields);
        return problem;
    }

    /**
     * Conversao de parametro que falhou: UUID invalido, data mal formada, numero
     * que nao cabe no tipo. Sem este handler cai no {@code catch-all} e o
     * cliente recebe 500 para o que e, na essencia, erro de quem chamou.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() == null ? "valor valido" : ex.getRequiredType().getSimpleName();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parametro '" + ex.getName() + "' invalido: esperado " + expected);
        problem.setTitle("Parametro invalido");
        problem.setType(URI.create("https://api.tradewind.exemplo.com/errors/type-mismatch"));
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    /**
     * Rota que nao existe. O Boot resolve isso como
     * {@code NoResourceFoundException}, que sem tratamento vira 500 e polui o
     * alerta de erro com o que e so um URL digitado errado.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Rota nao encontrada: " + ex.getResourcePath());
        problem.setTitle("Recurso nao encontrado");
        problem.setType(URI.create("https://api.tradewind.exemplo.com/errors/not-found"));
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
        problem.setTitle("Erro interno");
        problem.setType(URI.create("https://api.tradewind.exemplo.com/errors/internal"));
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }
}
