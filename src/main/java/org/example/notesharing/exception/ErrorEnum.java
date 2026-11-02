package org.example.notesharing.exception;

import org.springframework.http.HttpStatus;

public enum ErrorEnum {

    EMAIL_ALREADY_EXISTS(
            "A-10001",
            "An account with this email already exists",
            HttpStatus.CONFLICT
    ),

    USERNAME_ALREADY_EXISTS(
            "A-10006",
            "An account with this username already exists",
            HttpStatus.CONFLICT
    ),

    USER_NOT_FOUND(
            "A-10002",
            "The requested user was not found",
            HttpStatus.NOT_FOUND
    ),

    NOTE_NOT_FOUND(
            "A-10007",
            "The requested note was not found",
            HttpStatus.NOT_FOUND
    ),

    INVALID_CREDENTIALS(
            "A-10003",
            "The supplied credentials are invalid",
            HttpStatus.UNAUTHORIZED
    ),

    DATABASE_ERROR(
            "A-10004",
            "A database operation failed",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),

    INTERNAL_ERROR(
            "A-10005",
            "An unexpected error occurred",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorEnum(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
