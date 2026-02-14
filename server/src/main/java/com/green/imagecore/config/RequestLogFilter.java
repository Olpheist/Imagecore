package com.green.imagecore.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class RequestLogFilter implements Filter {

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        long start = System.currentTimeMillis();

        log.info(">>> {} {} from {}",
                req.getMethod(),
                req.getRequestURI(),
                req.getRemoteAddr()
        );

        chain.doFilter(request, response);

        long duration = System.currentTimeMillis() - start;

        log.info("<<< {} {} -> {} ({}ms)",
                req.getMethod(),
                req.getRequestURI(),
                res.getStatus(),
                duration
        );
    }
}
