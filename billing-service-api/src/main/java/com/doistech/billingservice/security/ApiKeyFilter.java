package com.doistech.billingservice.security;

import com.doistech.billingservice.config.ApiSecurityProperties;
import com.doistech.billingservice.core.merchant.AuthenticatedMerchant;
import com.doistech.billingservice.core.merchant.MerchantService;
import com.doistech.billingservice.shared.crypto.Hmac;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica merchants pela X-API-Key e a administração pela X-Admin-Key.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ApiSecurityProperties properties;
    private final MerchantService merchantService;

    public ApiKeyFilter(ApiSecurityProperties properties, MerchantService merchantService) {
        this.properties = properties;
        this.merchantService = merchantService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/api/webhooks")
                || path.startsWith("/api/health")
                || path.startsWith("/api/docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/admin")) {
            authenticateAdmin(request, response, filterChain);
            return;
        }

        Optional<AuthenticatedMerchant> merchant = merchantService.authenticate(request.getHeader(properties.apiKeyHeader()));
        if (merchant.isEmpty()) {
            reject(response, HttpStatus.UNAUTHORIZED, "invalid api key");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                merchant.get(),
                null,
                AuthorityUtils.createAuthorityList("ROLE_MERCHANT")));
        filterChain.doFilter(request, response);
    }

    private void authenticateAdmin(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!StringUtils.hasText(properties.adminApiKey())) {
            reject(response, HttpStatus.FORBIDDEN, "admin api is disabled");
            return;
        }
        if (!Hmac.constantTimeEquals(properties.adminApiKey(), request.getHeader(properties.adminApiKeyHeader()))) {
            reject(response, HttpStatus.UNAUTHORIZED, "invalid admin key");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
