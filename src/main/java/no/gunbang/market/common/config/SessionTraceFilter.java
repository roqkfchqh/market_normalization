package no.gunbang.market.common.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class SessionTraceFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession before = req.getSession(false);

        chain.doFilter(request, response);

        HttpSession after = req.getSession(false);
        if (after != null) {
            if (before == null) {
                log.info("세션이 새로 생성됨 URI: {}", req.getRequestURI());
            }
        }
    }
}
