package org.example.notesharing.exception;

public class SystemException extends RuntimeException {

    private final ErrorEnum errorEnum;

    public SystemException(ErrorEnum errorEnum) {
        super(errorEnum.getMessage());
        this.errorEnum = errorEnum;
    }

    public SystemException(ErrorEnum errorEnum, Throwable cause) {
        super(errorEnum.getMessage(), cause);
        this.errorEnum = errorEnum;
    }

    public ErrorEnum getErrorEnum() {
        return errorEnum;
    }
}
