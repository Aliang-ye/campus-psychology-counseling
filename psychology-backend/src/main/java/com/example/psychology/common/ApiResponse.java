package com.example.psychology.common;

import java.util.HashMap;
import java.util.Map;

public final class ApiResponse {
    private ApiResponse() {}

    public static Map<String, Object> success() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    public static Map<String, Object> success(String key, Object value) {
        Map<String, Object> result = success();
        result.put(key, value);
        return result;
    }

    public static Map<String, Object> successMessage(String message) {
        Map<String, Object> result = success();
        result.put("message", message);
        return result;
    }

    public static Map<String, Object> fail(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}
