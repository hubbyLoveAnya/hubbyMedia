package com.hubby.audiorecord.exception;

import androidx.annotation.NonNull;

public class PermissionLessException extends Exception {
    private String errorInfo;

    public PermissionLessException(String errorInfo) {
        this.errorInfo = errorInfo;
    }

    @NonNull
    @Override
    public String toString() {
        return "ErrorInfo:" + errorInfo;
    }
}
