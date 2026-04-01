package com.dinukaly.velo.advisor;

import com.dinukaly.velo.dto.APIResponse;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.exception.DuplicateResourceException;
import com.dinukaly.velo.exception.BadRequestException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    //username password not found
    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public APIResponse handleUsernameNotFoundException(UsernameNotFoundException ex) {
        return new APIResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage());
    }

    //bad credentials
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public APIResponse handleBadCredentialsException(BadCredentialsException ex) {
        return new APIResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage());
    }

    // Exception Handler for JWT Token Expired Exception
    @ExceptionHandler(ExpiredJwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public APIResponse handleJWTTokenExpiredException(ExpiredJwtException ex){
        return new APIResponse(401, "JWT Token Expired",null);
    }

    // Exception Handler for file system errors
    @ExceptionHandler(UncheckedIOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public APIResponse handleUncheckedIOException(UncheckedIOException ex) {
        return new APIResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "File system operation failed",
                ex.getMessage()
        );
    }

    // Exception Handler for general I/O errors
    @ExceptionHandler(java.io.IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public APIResponse handleIOException(java.io.IOException ex) {
        return new APIResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "I/O operation failed",
                ex.getMessage()
        );
    }

    // Exception Handler for access denied
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public APIResponse handleAccessDenied(AccessDeniedException ex) {
        return new APIResponse(
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                ex.getMessage()
        );
    }

    // Exception Handler for validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public APIResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return new APIResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed - please check the input fields",
                errors
        );
    }

    // Exception Handler for entity not found
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public APIResponse handleEntityNotFound(EntityNotFoundException ex) {
        return new APIResponse(
                HttpStatus.NOT_FOUND.value(),
                "Resource not found",
                ex.getMessage()
        );
    }

    // Exception Handler for all other exceptions (catch-all)
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public APIResponse handleAllExceptions(RuntimeException ex) {
        return new APIResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error",
                ex.getMessage()
        );

    }

    // Exception Handler for custom not found
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public APIResponse handleCustomNotFound(NotFoundException ex) {
        return new APIResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage()
        );
    }

    // Exception Handler for custom authentication
    @ExceptionHandler(CustomAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public APIResponse handleCustomAuthentication(CustomAuthenticationException ex) {
        return new APIResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ex.getMessage()
        );
    }

    // Exception Handler for duplicate resource
    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public APIResponse handleDuplicateResource(DuplicateResourceException ex) {
        return new APIResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage()
        );
    }

    // Exception Handler for bad request
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public APIResponse handleCustomBadRequest(BadRequestException ex) {
        return new APIResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage()
        );
    }
}
