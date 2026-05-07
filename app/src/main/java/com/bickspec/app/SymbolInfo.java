package com.bickspec.app;

public record SymbolInfo(
        String name,
        String type,
        String scope,
        int declaredAt,
        boolean initialized,
        String notes) {
}
