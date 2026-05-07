package com.bickspec.app;

public record CompilerDiagnostic(
        Severity severity,
        String code,
        String message,
        int line,
        int column) {
    public String formatted() {
        String location = line > 0 && column >= 0
                ? " at line " + line + ":" + column
                : "";
        return "[" + severity + "] " + code + " - " + message + location;
    }

    public enum Severity {
        ERROR,
        WARNING
    }
}
