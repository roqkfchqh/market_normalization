package no.gunbang.market.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 비즈니스 에러
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, Object>> handleBaseException(BaseException e) {
        return buildErrorResponse(e.getStatus(), e.getMessage());
    }

    // 잘못된 Http Method 처리
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpServletRequest request, HttpRequestMethodNotSupportedException e) {
        String errorMessage = "요청한 HTTP 메서드는 지원되지 않습니다.";
        warnLogger(request, errorMessage);
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, errorMessage);
    }

    // 잘못된 엔드포인트 요청 처리 (404)
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFoundException(
            HttpServletRequest request, NoHandlerFoundException e) {
        String errorMessage = "요청한 리소스를 찾을 수 없습니다: " + request.getRequestURI();
        warnLogger(request, errorMessage);
        return buildErrorResponse(HttpStatus.NOT_FOUND, errorMessage);
    }

    // 파라미터 존재하지 않을 때 발생
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameterException(
        HttpServletRequest request, MissingServletRequestParameterException e) {
        String errorMessage = e.getParameterName() + " 값이 누락되었습니다.";
        warnLogger(request, errorMessage);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    // 파라미터 타입과 일치하지 않을 때 발생
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
        HttpServletRequest request, MethodArgumentTypeMismatchException e) {
        String errorMessage = String.format("파라미터 타입 불일치: %s (기대된 타입: %s, 실제 값: %s)", e.getName(),
            Objects.requireNonNull(e.getRequiredType()).getSimpleName(), e.getValue());
        warnLogger(request, errorMessage);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    // HTTP 요청의 본문을 읽을 수 없을 때 발생
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
        HttpServletRequest request, HttpMessageNotReadableException e) {
        warnLogger(request, e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // @Valid 에러
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
        HttpServletRequest request, MethodArgumentNotValidException e) {
        // 글로벌 에러 메시지들
        String globalErrorMessage = e.getGlobalErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(", ", "[Global Error : ", "], \t"));

        // 필드 에러 메시지들
        String fieldErrorMessage = e.getFieldErrors().stream()
            .map(error -> error.getField() + " : " + error.getDefaultMessage())
            .collect(Collectors.joining(" ", "[Field Error : ", "]"));

        String errorMessage = globalErrorMessage + fieldErrorMessage;
        warnLogger(request, errorMessage);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    // 500 서버에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
        HttpServletRequest request,
        Exception e
    ) {
        log.error("예상하지 못한 예외가 발생했습니다. URI:{}, 내용:{}", request.getRequestURI(), e.getMessage(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버가 응답할 수 없습니다.");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status,
        String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private static void warnLogger(HttpServletRequest request, String errorMessage) {
        log.warn("잘못된 요청이 들어왔습니다. URI:{}, 내용:{}", request.getRequestURI(), errorMessage);
    }
}
