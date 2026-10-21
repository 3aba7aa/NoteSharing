package org.example.notesharing.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/errors")
public class ErrorController {

    public record ErrorDefinition(
            String code,
            String message,
            int status
    ) {
    }

    @GetMapping
    public List<ErrorDefinition> getAllErrors() {
        return Arrays.stream(ErrorEnum.values())
                .map(error -> new ErrorDefinition(
                        error.getCode(),
                        error.getMessage(),
                        error.getHttpStatus().value()
                ))
                .toList();
    }
}
