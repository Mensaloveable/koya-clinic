package com.clinic.koya.koya_clinic_api.exception;

import java.io.Serial;

public class ApiFailure extends  RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;

    public  ApiFailure(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
