package com.bickspec.app;

import com.bickspec.grammar.BickSpecParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TranspileRunner {
    private static final Path GENERATED_DIRECTORY = Path.of("testing", "generated");

    private TranspileRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.TranspileRunner <path-to-file-or-directory>");
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
                    Path generatedFile = transpile(file, result);
                    System.out.printf("[JAVA] %s%n", BickSpecParseSupport.formatPathForDisplay(generatedFile));
                    System.out.println("[ACTION] Java generation completed successfully");
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

    private static Path transpile(Path sourceFile, BickSpecParseSupport.ParseResult result) {
        try {
            Files.createDirectories(GENERATED_DIRECTORY);
            String className = generatedClassName(sourceFile);
            BickSpecJavaTranslatorVisitor translator = new BickSpecJavaTranslatorVisitor(
                    className,
                    BickSpecParseSupport.formatPathForDisplay(sourceFile));
            String javaSource = translator.translate((BickSpecParser.ProgramContext) result.tree());
            Path generatedFile = GENERATED_DIRECTORY.resolve(className + ".java");
            Files.writeString(generatedFile, javaSource, StandardCharsets.UTF_8);
            return generatedFile;
        } catch (IOException exception) {
            System.err.println(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.ERROR,
                    "GEN03",
                    "Failed to write generated Java file: " + exception.getMessage(),
                    -1,
                    -1).formatted());
            System.exit(1);
            return GENERATED_DIRECTORY;
        }
    }

    private static String generatedClassName(Path sourceFile) {
        String filename = sourceFile.getFileName().toString();
        String stem = filename.endsWith(".bks") ? filename.substring(0, filename.length() - 4) : filename;
        String sanitized = stem.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isBlank()) {
            sanitized = "BickSpecProgram";
        }
        if (!Character.isJavaIdentifierStart(sanitized.charAt(0))) {
            sanitized = "BickSpec_" + sanitized;
        }
        return sanitized + "_Generated";
    }
}
