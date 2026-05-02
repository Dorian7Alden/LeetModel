package leet.model.leetmodelbackend.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import leet.model.leetmodelbackend.common.Result;
import leet.model.leetmodelbackend.common.error.ResponseCode;
import leet.model.leetmodelbackend.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("token");

        if (token == null || token.isBlank()) {
            writeError(response, ResponseCode.AUTH_UNAUTHORIZED);
            return false;
        }

        if (!jwtUtil.isValid(token)) {
            writeError(response, ResponseCode.AUTH_INVALID_TOKEN);
            return false;
        }

        request.setAttribute("userId", jwtUtil.getSubject(token));
        return true;
    }

    private void writeError(HttpServletResponse response, ResponseCode responseCode) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.fail(responseCode);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
