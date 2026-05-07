package com.bickspec.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ParseRunner {
    private ParseRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp app/target/bickspec-compiler-1.0.0.jar com.bickspec.app.ParseRunner <path-to-file-or-directory>");
            System.exit(1);
        }

        Path inputPath = Path.of(args[0]);
        List<Path> files = BickSpecParseSupport.resolveInputFiles(inputPath);
        boolean hasFailure = false;

        for (Path file : files) {
            System.out.printf("==== %s ====%n", BickSpecParseSupport.formatPathForDisplay(file));
            BickSpecParseSupport.ParseResult result = BickSpecParseSupport.parseFile(file);
            if (result.success()) {
                System.out.println("[STATUS] PARSE OK");
                if (result.semanticResult().success()) {
                    System.out.println("[STATUS] SEMANTIC OK");
                    Path symbolsFile = exportSymbols(file, result.semanticResult().symbolTable());
                    System.out.printf("[SYMBOLS] %s%n", BickSpecParseSupport.formatPathForDisplay(symbolsFile));
                    ParseTreeGraphGenerator.GraphResult graphResult = ParseTreeGraphGenerator.generate(file, result);
                    if (graphResult.svgFile() != null) {
                        System.out.printf("[TREE] %s%n", BickSpecParseSupport.formatPathForDisplay(graphResult.svgFile()));
                    } else {
                        System.out.printf("[TREE] %s%n", graphResult.svgMessage());
                    }
                } else {
                    hasFailure = true;
                    System.out.println("[STATUS] SEMANTIC FAILED");
                    result.semanticResult().diagnostics().forEach(diagnostic -> System.out.println(diagnostic.formatted()));
                    System.out.println("[SYMBOLS] not generated");
                    System.out.println("[JAVA] not generated");
                }
            } else {
                hasFailure = true;
                System.out.println("[STATUS] PARSE FAILED");
                System.out.println(result.diagnostic().formatted());
                System.out.println("[SYMBOLS] not generated");
                System.out.println("[JAVA] not generated");
            }
        }

        if (hasFailure) {
            System.exit(1);
        }
    }

    private static Path exportSymbols(Path file, SymbolTable symbolTable) {
        try {
            return SymbolTableCsvExporter.export(file, symbolTable);
        } catch (IOException exception) {
            System.out.println(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.ERROR,
                    "GEN02",
                    "Failed to write symbol table CSV: " + exception.getMessage(),
                    -1,
                    -1).formatted());
            System.exit(1);
            return Path.of("testing", "symbols");
        }
    }
}
