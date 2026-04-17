package my.hive_back.common.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import my.hive_back.common.dto.Result;
import my.hive_back.common.utils.ResponseEncryptUtil;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
/**
 * ResultEncryptAdvice 属于小程序后端通用能力层，承担全局增强处理，例如统一响应或异常拦截。
 */
@ControllerAdvice
public class ResultEncryptAdvice implements ResponseBodyAdvice<Object> {

    private final ResponseEncryptUtil responseEncryptUtil;

    public ResultEncryptAdvice(ResponseEncryptUtil responseEncryptUtil) {
        this.responseEncryptUtil = responseEncryptUtil;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof Result result)) {
            return body;
        }
        if (Boolean.TRUE.equals(result.getEncrypted()) || result.getData() == null) {
            return result;
        }

        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
        if (!shouldEncrypt(servletRequest, selectedContentType, servletResponse)) {
            return result;
        }

        result.setData(responseEncryptUtil.encrypt(null, result.getData()));
        result.setEncrypted(true);
        result.setAlg(ResponseEncryptUtil.RESPONSE_ALGORITHM);
        return result;
    }

    private boolean shouldEncrypt(HttpServletRequest request, MediaType mediaType, HttpServletResponse response) {
        if (mediaType == null || !MediaType.APPLICATION_JSON.includes(mediaType)) {
            return false;
        }
        if (response.getContentType() != null && response.getContentType().contains("application/octet-stream")) {
            return false;
        }
        String path = request.getRequestURI();
        if (path != null && path.contains("/swagger")) {
            return false;
        }
        return true;
    }
}
