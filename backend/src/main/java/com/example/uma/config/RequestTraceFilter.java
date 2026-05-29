package com.example.uma.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
  private static final String TRACE_ID_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String traceId = Optional.ofNullable(request.getHeader(TRACE_ID_HEADER))
        .filter(value -> !value.isBlank())
        .orElseGet(() -> UUID.randomUUID().toString());
    long startedAt = System.nanoTime();

    MDC.put(TRACE_ID_KEY, traceId);
    response.setHeader(TRACE_ID_HEADER, traceId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
      log.info(
          "http_request method={} path={} status={} durationMs={} traceId={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs,
          traceId
      );
      MDC.remove(TRACE_ID_KEY);
    }
  }
}
