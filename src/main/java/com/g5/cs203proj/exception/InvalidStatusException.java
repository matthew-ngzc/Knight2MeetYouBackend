package com.g5.cs203proj.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidStatusException extends RuntimeException{
    private static final long serialVersionUID = 1L;

    public InvalidStatusException(String msg) {
        super(msg);
    }
}