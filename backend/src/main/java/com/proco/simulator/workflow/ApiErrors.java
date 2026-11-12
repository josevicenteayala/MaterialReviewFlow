package com.proco.simulator.workflow;
import com.proco.simulator.shared.RuleException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(RuleException.class) ResponseEntity<?> rule(RuleException e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage(),"status",e.status));}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingRequestHeaderException.class}) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Solicitud inválida: verifica campos, formatos y cabecera Idempotency-Key","status",400));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e){return ResponseEntity.status(409).body(Map.of("message","Conflicto de integridad; actualiza e intenta nuevamente","status",409));}
}
