package com.sgs.capability.dto;

/** ABP-style response envelope used by the original frontend. */
public class AbpResponse<T> {
    public T result;
    public boolean success;
    public Object error;
    public String targetUrl;
    public boolean unAuthorizedRequest;
    public boolean __abp;

    public static <T> AbpResponse<T> ok(T result) {
        AbpResponse<T> response = new AbpResponse<>();
        response.result = result;
        response.success = true;
        response.error = null;
        response.targetUrl = null;
        response.unAuthorizedRequest = false;
        response.__abp = true;
        return response;
    }

    public static <T> AbpResponse<T> denied(String message) {
        AbpResponse<T> response = new AbpResponse<>();
        response.result = null;
        response.success = false;
        response.error = new ErrorInfo(message);
        response.targetUrl = null;
        response.unAuthorizedRequest = true;
        response.__abp = true;
        return response;
    }

    public static <T> AbpResponse<T> failed(String message) {
        AbpResponse<T> response = denied(message);
        response.unAuthorizedRequest = false;
        return response;
    }

    public record ErrorInfo(String message) {
    }
}
