package com.workoutdone.rpgym.common.exception;

import com.workoutdone.rpgym.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 공통 비즈니스 예외 처리
     *
     * ErrorCode를 구현한 모든 예외가 이 핸들러에서 처리된다.
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            BaseException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        logException(errorCode, exception);

        return createResponse(errorCode);
    }

    /**
     * Bean Validation 검증 실패 처리
     *
     * @NotBlank, @Size, @Min 등의 검증 실패 시
     * 필드별 오류 정보를 응답에 포함한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        List<ErrorResponse.FieldError> fields =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error ->
                                new ErrorResponse.FieldError(
                                        error.getField(),
                                        error.getDefaultMessage()
                                )
                        )
                        .toList();

        log.warn(
                "Validation failed: fields={}",
                fields
        );

        ErrorResponse response = ErrorResponse.of(
                CommonErrorCode.INVALID_INPUT.getCode(),
                CommonErrorCode.INVALID_INPUT.getMessage(),
                getTraceId(),
                fields
        );

        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT.getStatus())
                .body(response);
    }

    /**
     * 요청 본문을 읽을 수 없는 경우 처리
     *
     * 잘못된 JSON 형식이나 요청 본문 파싱 실패 등을 처리한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        log.warn(
                "Request body could not be parsed: {}",
                exception.getMessage()
        );

        return createResponse(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 요청 파라미터의 타입 변환에 실패한 경우 처리
     *
     * 예:
     * UUID가 필요한 경로 변수에 올바르지 않은 값이 전달된 경우
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        log.warn(
                "Request parameter type mismatch: {}",
                exception.getMessage()
        );

        return createResponse(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 지원하지 않는 HTTP Method 요청 처리
     *
     * 예:
     * POST API에 GET 요청을 보내는 경우
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        log.warn(
                "HTTP method not supported: method={}, supported={}",
                exception.getMethod(),
                exception.getSupportedHttpMethods()
        );

        return createResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 필수 Query Parameter가 누락된 경우 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        log.warn(
                "Required request parameter missing: parameter={}",
                exception.getParameterName()
        );

        return createResponse(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 지원하지 않는 Content-Type 요청 처리
     *
     * 예:
     * application/json을 지원하는 API에
     * 지원하지 않는 Media Type을 전달한 경우
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception
    ) {
        log.warn(
                "Unsupported media type: {}",
                exception.getContentType()
        );

        return createResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * 존재하지 않는 API 또는 리소스 요청 처리
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        log.warn(
                "Resource not found: path={}",
                exception.getResourcePath()
        );

        return createResponse(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 낙관적 락(@Version) 충돌 처리
     *
     * 같은 row를 동시에 수정하는 두 트랜잭션 중 나중에 커밋을 시도하는 쪽에서 발생한다.
     * 먼저 커밋된 변경을 조용히 덮어쓰지 않도록 여기서 막고, 클라이언트가 최신 상태를 다시 읽고
     * 재시도하도록 409로 응답한다.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleObjectOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException exception
    ) {
        log.warn(
                "Optimistic locking conflict: entity={}, id={}",
                exception.getPersistentClassName(),
                exception.getIdentifier()
        );

        return createResponse(CommonErrorCode.CONFLICT);
    }

    /**
     * 별도로 처리되지 않은 예외를 최종적으로 처리한다.
     *
     * 구체적인 예외 핸들러에서 처리되지 않은 예상하지 못한 예외는
     * 서버 오류로 간주하고 500 ErrorResponse를 반환한다.
     *
     * 클라이언트에는 상세 예외 정보를 노출하지 않고,
     * 서버 로그에는 실제 예외와 StackTrace를 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception
    ) {
        log.error(
                "Unexpected server exception: type={}",
                exception.getClass().getSimpleName(),
                exception
        );

        return createResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    /**
     * ErrorResponse를 생성하고 HTTP 상태 코드를 설정한다.
     */
    private ResponseEntity<ErrorResponse> createResponse(
            ErrorCode errorCode
    ) {
        ErrorResponse response = ErrorResponse.of(
                errorCode.getCode(),
                errorCode.getMessage(),
                getTraceId()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(response);
    }

    /**
     * 현재 요청의 traceId를 MDC에서 조회한다.
     */
    private String getTraceId() {
        return MDC.get("traceId");
    }

    /**
     * 비즈니스 예외의 HTTP 상태에 따라 로그 레벨을 구분한다.
     *
     * 4xx: 클라이언트 요청 또는 비즈니스 조건에 따른 예외이므로 WARN
     * 5xx: 서버 측 오류이므로 ERROR + StackTrace
     */
    private void logException(
            ErrorCode errorCode,
            BaseException exception
    ) {

        if (errorCode.getStatus().is4xxClientError()) {
            log.warn(
                    "Business exception: code={}, message={}",
                    errorCode.getCode(),
                    errorCode.getMessage()
            );
        } else {
            log.error(
                    "Business exception: code={}, message={}",
                    errorCode.getCode(),
                    errorCode.getMessage(),
                    exception
            );
        }
    }
}