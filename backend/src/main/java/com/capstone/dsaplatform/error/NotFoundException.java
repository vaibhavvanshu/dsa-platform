package com.capstone.dsaplatform.error;

/** Maps to 404 with error code NOT_FOUND (CONTRACT.md section 0). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
