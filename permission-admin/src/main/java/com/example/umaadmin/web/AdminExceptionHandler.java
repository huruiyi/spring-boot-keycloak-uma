package com.example.umaadmin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ControllerAdvice
public class AdminExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AdminExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleException(Exception exception, HttpServletRequest request, Model model) {
    String errorId = UUID.randomUUID().toString();
    log.error(
        "Permission admin request failed. errorId={}, method={}, uri={}",
        errorId,
        request.getMethod(),
        request.getRequestURI(),
        exception
    );
    model.addAttribute("errorId", errorId);
    model.addAttribute("path", request.getRequestURI());
    model.addAttribute("message", rootMessage(exception));
    return "error";
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? throwable.getClass().getSimpleName() : current.getMessage();
  }
}
