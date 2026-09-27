package ir.corevia;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import ir.corevia.accounts.AccountException;
import ir.corevia.accounts.AccountNotFoundException;
import ir.corevia.ledger.IdempotencyConflictException;
import ir.corevia.ledger.InvalidPostingException;
import ir.corevia.ledger.JournalAlreadyReversedException;
import ir.corevia.ledger.JournalNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, JournalNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), "not-found");
    }

    @ExceptionHandler({IdempotencyConflictException.class, JournalAlreadyReversedException.class,
            DataIntegrityViolationException.class})
    ProblemDetail conflict(RuntimeException exception) {
        String detail = exception instanceof DataIntegrityViolationException
                ? "The request conflicts with existing financial data" : exception.getMessage();
        return problem(HttpStatus.CONFLICT, "Financial state conflict", detail, "conflict");
    }

    @ExceptionHandler({InvalidPostingException.class, AccountException.class})
    ProblemDetail unprocessable(RuntimeException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation",
                exception.getMessage(), "business-rule-violation");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                "One or more request fields are invalid", "validation-failed");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Required header is missing",
                exception.getHeaderName() + " is required", "missing-header");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://corevia.ir/problems/" + type));
        return problem;
    }
}
