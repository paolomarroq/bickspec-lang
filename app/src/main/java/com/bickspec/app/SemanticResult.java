package com.bickspec.app;

import java.util.List;

public record SemanticResult(
        boolean success,
        SymbolTable symbolTable,
        List<String> trace,
        List<CompilerDiagnostic> diagnostics) {
}
