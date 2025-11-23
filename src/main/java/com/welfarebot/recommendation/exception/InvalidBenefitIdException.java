package com.welfarebot.recommendation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBenefitIdException extends IllegalArgumentException {

    public InvalidBenefitIdException(String message) {
        super(message);
    }
}
