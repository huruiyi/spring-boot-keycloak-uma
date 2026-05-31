package com.example.umaadmin.audit;

import com.example.umaadmin.service.PermissionAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

@Component
public class AuditLogInterceptor implements HandlerInterceptor {

  private static final String BEFORE_MODEL = AuditLogInterceptor.class.getName() + ".beforeModel";
  private static final List<String> SKIPPED_POST_PATHS = List.of("/login", "/logout", "/model-sync/preview");

  private final PermissionAdminService service;
  private final AuditLogService auditLogService;

  public AuditLogInterceptor(PermissionAdminService service, AuditLogService auditLogService) {
    this.service = service;
    this.auditLogService = auditLogService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (shouldAudit(request)) {
      request.setAttribute(BEFORE_MODEL, service.modelJson());
    }
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (!shouldAudit(request) || ex != null || response.getStatus() >= 400) {
      return;
    }
    String before = (String) request.getAttribute(BEFORE_MODEL);
    String after = service.modelJson();
    if (Objects.equals(before, after)) {
      return;
    }
    Principal principal = request.getUserPrincipal();
    auditLogService.record(
        principal == null ? "anonymous" : principal.getName(),
        remoteIp(request),
        request.getMethod() + " " + request.getRequestURI(),
        target(request),
        before,
        after
    );
  }

  private boolean shouldAudit(HttpServletRequest request) {
    return "POST".equalsIgnoreCase(request.getMethod()) && !SKIPPED_POST_PATHS.contains(request.getRequestURI());
  }

  private String target(HttpServletRequest request) {
    for (String parameter : List.of("name", "username", "method", "path")) {
      String value = request.getParameter(parameter);
      if (value != null && !value.isBlank()) {
        return parameter + "=" + value;
      }
    }
    return request.getRequestURI();
  }

  private String remoteIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",", 2)[0].trim();
    }
    return request.getRemoteAddr();
  }
}
