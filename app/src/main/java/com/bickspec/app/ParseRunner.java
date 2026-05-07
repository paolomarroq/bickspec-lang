package com.bickspec.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Phase II command-line entry point for parser validation and trace reporting.
 *
 * <p>The runner accepts one {@code .bks} file or a directory, delegates lexical
 * and syntactic validation to {@link BickSpecParseSupport}, and only continues
 * with Phase II outputs when parsing succeeds. For each valid file it prints the
 * ANTLR parse tree, prints the semantic visit trace, and asks
 * {@link ParseTreeGraphGenerator} to write DOT/SVG parse tree visualizations.</p>
 *
 * <p>Input: BickSpec source files. Output: console validation results, semantic
 * trace lines, and parse tree graph files under {@code testing/trees/}. Invalid
 * files preserve the current failure behavior and do not produce graph files.
 * Complete semantic validation and runtime behavior are intentionally left for
 * Phase III.</p>
 */
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
                    System.out.println("Semantic visit trace:");
                    result.semanticResult().trace().forEach(System.out::println);
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
