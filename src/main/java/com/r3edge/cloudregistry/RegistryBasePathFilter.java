package com.r3edge.cloudregistry;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

//@Component
public class RegistryBasePathFilter extends OncePerRequestFilter {

    private final ServiceRegistryProperties properties;

    public RegistryBasePathFilter(ServiceRegistryProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String basePath = properties.getBasePath();
        String uri = request.getRequestURI();

        if (basePath != null && uri.startsWith(basePath)) {
            String newUri = uri.substring(basePath.length());
            HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
                @Override
                public String getRequestURI() {
                    return newUri.isEmpty() ? "/" : newUri;
                }

                @Override
                public String getServletPath() {
                    return newUri;
                }
            };
            filterChain.doFilter(wrapped, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}

