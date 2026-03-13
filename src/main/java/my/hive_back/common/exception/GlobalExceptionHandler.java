package my.hive_back.common.exception;

import my.hive_back.common.dto.ResultDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（适配jakarta.validation的@Valid校验异常）
 * RestControllerAdvice：捕获所有@RestController的异常
 * Slf4j：日志打印
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理@Valid + @RequestBody的参数校验失败（POST/PUT JSON入参）
     * 返回HTTP 400 + 业务码400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultDTO<Void>> handleRequestBodyValidException(MethodArgumentNotValidException e) {
        // 获取校验失败的字段信息
        BindingResult bindingResult = e.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();

        // 拼接友好的错误提示
        String errorMsg = fieldErrors.stream()
                .map(error -> String.format("字段[%s]：%s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("；"));

        // 打印详细日志（便于排查）
        log.error("JSON参数校验失败：{}", errorMsg, e);

        // 构建统一响应体，返回HTTP 400状态码
        ResultDTO<Void> result = ResultDTO.fail(400, errorMsg);
        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理@Valid + @RequestParam/@PathVariable的参数校验失败（GET路径/查询参数）
     * 返回HTTP 400 + 业务码400
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResultDTO<Void>> handleRequestParamValidException(ConstraintViolationException e) {
        // 获取参数校验失败信息
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();

        // 拼接错误提示（简化参数名，去掉方法前缀）
        String errorMsg = violations.stream()
                .map(violation -> {
                    // 截取参数名（如"getOrder.id" -> "id"）
                    String paramName = violation.getPropertyPath().toString();
                    if (paramName.contains(".")) {
                        paramName = paramName.substring(paramName.lastIndexOf(".") + 1);
                    }
                    return String.format("参数[%s]：%s", paramName, violation.getMessage());
                })
                .collect(Collectors.joining("；"));

        log.error("路径/查询参数校验失败：{}", errorMsg, e);
        ResultDTO<Void> result = ResultDTO.fail(400, errorMsg);
        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理自定义业务异常
     * 返回HTTP 200 + 自定义业务码（业务异常不改变HTTP状态码）
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResultDTO<Void>> handleBusinessException(BusinessException e) {
        // 业务异常只打印warn日志（非系统错误）
        log.warn("业务异常：{}", e.getMsg());
        ResultDTO<Void> result = ResultDTO.fail(e.getCode(), e.getMsg());
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * 兜底异常处理（捕获所有未定义的异常）
     * 返回HTTP 500 + 业务码500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultDTO<Void>> handleGlobalException(Exception e) {
        // 系统异常打印error日志（包含堆栈）
        log.error("系统内部异常", e);
        // 对外隐藏具体异常信息，只返回友好提示
        ResultDTO<Void> result = ResultDTO.fail(500, "服务器内部错误，请稍后重试");
        return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理参数类型转换异常（如String转Long失败）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultDTO<Void>> handleIllegalArgException(IllegalArgumentException e) {
        log.error("参数格式错误：{}", e.getMessage(), e);
        ResultDTO<Void> result = ResultDTO.fail(400, "参数格式错误：" + e.getMessage());
        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理空指针异常（兜底补充）
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ResultDTO<Void>> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常", e);
        ResultDTO<Void> result = ResultDTO.fail(500, "服务器内部错误，请稍后重试");
        return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 处理无权限异常
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResultDTO<Void>> handleAccessDenied(AccessDeniedException e) {
        ResultDTO<Void> result = ResultDTO.fail(403, "无权限");
        return new ResponseEntity<>(result, HttpStatus.FORBIDDEN);
    }
}