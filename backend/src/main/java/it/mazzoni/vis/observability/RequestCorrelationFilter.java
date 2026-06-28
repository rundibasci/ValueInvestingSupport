package it.mazzoni.vis.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private ObservabilityProperties properties = new ObservabilityProperties(true, "X-Correlation-ID");

    public RequestCorrelationFilter() {
    }

    public RequestCorrelationFilter(ObservabilityProperties properties) {
        this.properties = properties;
    }

    @Autowired(required = false)
    public void setProperties(ObservabilityProperties properties) {
        if (properties != null) {
            this.properties = properties;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        long start = System.nanoTime();
        MDC.put("correlation.id", correlationId);
        response.setHeader(properties.correlationHeader(), correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (properties.requestLoggingEnabled()) {
                log.info("http_request method={} path={} status={} durationMs={} role={}",
                        request.getMethod(),
                        safePath(request.getRequestURI()),
                        response.getStatus(),
                        durationMs,
                        currentRole());
            }
            MDC.remove("correlation.id");
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(properties.correlationHeader());
        if (incoming != null && incoming.matches("[A-Za-z0-9_.:-]{8,128}")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private String currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .orElse("authenticated");
    }

    private String safePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.replaceAll("[?].*$", "");
    }
}
