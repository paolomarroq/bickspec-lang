package com.bickspec.app;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SymbolTable {
    private final Map<String, SymbolInfo> symbols = new LinkedHashMap<>();

    public boolean declare(SymbolInfo symbol) {
        String key = key(symbol.scope(), symbol.name());
        if (symbols.containsKey(key)) {
            return false;
        }
        symbols.put(key, symbol);
        return true;
    }

    public SymbolInfo lookup(String name, String scope) {
        SymbolInfo scoped = symbols.get(key(scope, name));
        if (scoped != null) {
            return scoped;
        }
        return symbols.get(key("global", name));
    }

    public SymbolInfo lookupInScope(String name, String scope) {
        return symbols.get(key(scope, name));
    }

    public Collection<SymbolInfo> symbols() {
        return symbols.values();
    }

    private static String key(String scope, String name) {
        return scope + "::" + name;
    }
}
