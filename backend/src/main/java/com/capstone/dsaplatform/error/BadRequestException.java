package com.capstone.dsaplatform.error;

/** Maps to 400 with error code BAD_REQUEST (CONTRACT.md section 0). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
