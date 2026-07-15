package it.mazzoni.vis.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AdminUserController.class)
public class AdminUserExceptionHandler {
    @ExceptionHandler(UserLifecycleException.class)
    ResponseEntity<Map<String, String>> handle(UserLifecycleException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage()
        ));
    }
}
