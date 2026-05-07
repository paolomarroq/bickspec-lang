package com.bickspec.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SymbolTableCsvExporter {
    private static final Path SYMBOL_DIRECTORY = Path.of("output", "symbols");

    private SymbolTableCsvExporter() {
    }

    public static Path export(Path sourceFile, SymbolTable symbolTable) throws IOException {
        Files.createDirectories(SYMBOL_DIRECTORY);
        Path csvFile = SYMBOL_DIRECTORY.resolve(symbolBaseName(sourceFile) + "_symbols.csv");
        StringBuilder csv = new StringBuilder("name,type,scope,declared_at,initialized,notes\n");
        for (SymbolInfo symbol : symbolTable.symbols()) {
            csv.append(escape(symbol.name())).append(",")
                    .append(escape(symbol.type())).append(",")
                    .append(escape(symbol.scope())).append(",")
                    .append(symbol.declaredAt()).append(",")
                    .append(symbol.initialized()).append(",")
                    .append(escape(symbol.notes()))
                    .append("\n");
        }
        Files.writeString(csvFile, csv.toString(), StandardCharsets.UTF_8);
        return csvFile;
    }

    private static String symbolBaseName(Path sourceFile) {
        String filename = sourceFile.getFileName().toString();
        String stem = filename.endsWith(".bks") ? filename.substring(0, filename.length() - 4) : filename;
        String sanitized = stem.replaceAll("[^A-Za-z0-9_]", "_");
        return sanitized.isBlank() ? "BickSpecProgram" : sanitized;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }
}
