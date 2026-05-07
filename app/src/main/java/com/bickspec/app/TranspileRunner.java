package com.bickspec.app;

import com.bickspec.grammar.BickSpecParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TranspileRunner {
    private static final Path GENERATED_DIRECTORY = Path.of("output", "java");
    private static final Path CLASS_DIRECTORY = Path.of("output", "classes");
    private static final Path SUMMARY_REPORT = Path.of("output", "reports", "generation_summary.csv");

    private TranspileRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar app/target/bickspec-compiler-1.0.0.jar <path-to-file-or-directory>");
            System.exit(1);
        }

        Path inputPath = Path.of(args[0]);
        List<Path> files = BickSpecParseSupport.resolveInputFiles(inputPath);
        List<SummaryRow> summaryRows = new ArrayList<>();
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
                    String className = generatedClassName(file);
                    Path classFile = compileGeneratedJava(generatedFile, className);
                    System.out.printf("[BUILD] %s%n", BickSpecParseSupport.formatPathForDisplay(classFile));
                    boolean interactiveMode = sourceRequiresInput(file);
                    if (interactiveMode) {
                        System.out.println("[EXECUTION] interactive mode");
                    }
                    ExecutionResult executionResult = executeGeneratedJava(className, interactiveMode);
                    if (executionResult.success()) {
                        System.out.println("[EXECUTION] completed successfully");
                    } else {
                        hasFailure = true;
                        System.out.println("[EXECUTION] failed");
                    }
                    printProgramOutput(executionResult);
                    summaryRows.add(SummaryRow.generated(
                            file,
                            symbolsFile,
                            graphResult.svgFile(),
                            generatedFile,
                            classFile,
                            executionResult.success() ? "OK" : "FAILED"));
                } else {
                    hasFailure = true;
                    deleteGeneratedJavaIfPresent(file);
                    deleteGeneratedClassIfPresent(file);
                    System.out.println("[STATUS] SEMANTIC FAILED");
                    result.semanticResult().diagnostics().forEach(diagnostic -> System.out.println(diagnostic.formatted()));
                    System.out.println("[SYMBOLS] not generated");
                    System.out.println("[JAVA] not generated");
                    summaryRows.add(SummaryRow.failed(
                            file,
                            "OK",
                            "FAILED",
                            "not generated",
                            "not generated",
                            "not generated",
                            "not compiled",
                            "not run"));
                }
            } else {
                hasFailure = true;
                deleteGeneratedJavaIfPresent(file);
                deleteGeneratedClassIfPresent(file);
                System.out.println("[STATUS] PARSE FAILED");
                System.out.println(result.diagnostic().formatted());
                System.out.println("[SYMBOLS] not generated");
                System.out.println("[JAVA] not generated");
                summaryRows.add(SummaryRow.failed(
                        file,
                        "FAILED",
                        "not run",
                        "not generated",
                        "not generated",
                        "not generated",
                        "not compiled",
                        "not run"));
            }
        }

        if (Files.isDirectory(inputPath)) {
            writeSummaryReport(summaryRows);
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
            return Path.of("output", "symbols");
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

    private static void deleteGeneratedJavaIfPresent(Path sourceFile) {
        try {
            Files.deleteIfExists(GENERATED_DIRECTORY.resolve(generatedClassName(sourceFile) + ".java"));
        } catch (IOException exception) {
            System.err.println(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.WARNING,
                    "GEN05",
                    "Failed to remove stale generated Java file: " + exception.getMessage(),
                    -1,
                    -1).formatted());
        }
    }

    private static void deleteGeneratedClassIfPresent(Path sourceFile) {
        try {
            Files.deleteIfExists(CLASS_DIRECTORY.resolve(generatedClassName(sourceFile) + ".class"));
        } catch (IOException exception) {
            System.err.println(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.WARNING,
                    "GEN06",
                    "Failed to remove stale generated class file: " + exception.getMessage(),
                    -1,
                    -1).formatted());
        }
    }

    private static Path compileGeneratedJava(Path generatedFile, String className) {
        try {
            Files.createDirectories(CLASS_DIRECTORY);
            Process process = new ProcessBuilder(
                    "javac",
                    "-encoding",
                    "UTF-8",
                    "-d",
                    CLASS_DIRECTORY.toString(),
                    generatedFile.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (!output.isBlank()) {
                printIndented("[JAVAC]", output);
            }
            if (exitCode != 0) {
                System.err.println(new CompilerDiagnostic(
                        CompilerDiagnostic.Severity.ERROR,
                        "GEN06",
                        "Generated Java compilation failed",
                        -1,
                        -1).formatted());
                System.exit(1);
            }
            return CLASS_DIRECTORY.resolve(className + ".class");
        } catch (IOException exception) {
            System.err.println(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.ERROR,
                    "GEN06",
                    "Failed to run javac: " + exception.getMessage(),
                    -1,
                    -1).formatted());
            System.exit(1);
            return CLASS_DIRECTORY.resolve(className + ".class");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.ERROR,
                    "GEN06",
                    "Generated Java compilation was interrupted",
                    -1,
                    -1).formatted());
            System.exit(1);
            return CLASS_DIRECTORY.resolve(className + ".class");
        }
    }

    private static ExecutionResult executeGeneratedJava(String className, boolean interactiveMode) {
        try {
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-cp");
            command.add(CLASS_DIRECTORY.toString());
            command.add(className);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (interactiveMode) {
                processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = processBuilder.start();
            if (interactiveMode) {
                int exitCode = process.waitFor();
                return new ExecutionResult(exitCode == 0, "", "", false);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ExecutionResult(exitCode == 0, stdout, stderr, true);
        } catch (IOException exception) {
            return new ExecutionResult(false, "", "Failed to run generated Java: " + exception.getMessage(), true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(false, "", "Generated Java execution was interrupted", true);
        }
    }

    private static boolean sourceRequiresInput(Path sourceFile) {
        try {
            return Files.readString(sourceFile, StandardCharsets.UTF_8).matches("(?s).*\\bREAD\\b.*");
        } catch (IOException exception) {
            return false;
        }
    }

    private static void printProgramOutput(ExecutionResult executionResult) {
        if (!executionResult.stdout().isBlank()) {
            printOutputBox("PROGRAM OUTPUT", executionResult.stdout());
        }
        if (!executionResult.stderr().isBlank()) {
            printIndented("[PROGRAM-ERROR]", executionResult.stderr());
        }
    }

    private static void printOutputBox(String title, String output) {
        List<String> lines = output.lines().toList();
        int contentWidth = title.length() + 4;
        for (String line : lines) {
            contentWidth = Math.max(contentWidth, line.length());
        }
        contentWidth = Math.min(Math.max(contentWidth, 48), 100);

        String top = boxTop(title, contentWidth);
        String bottom = "+" + "-".repeat(contentWidth + 2) + "+";
        System.out.println(top);
        for (String line : lines) {
            if (line.length() <= contentWidth) {
                System.out.printf("| %-" + contentWidth + "s |%n", line);
                continue;
            }
            for (int index = 0; index < line.length(); index += contentWidth) {
                String part = line.substring(index, Math.min(index + contentWidth, line.length()));
                System.out.printf("| %-" + contentWidth + "s |%n", part);
            }
        }
        System.out.println(bottom);
    }

    private static String boxTop(String title, int contentWidth) {
        String label = " " + title + " ";
        int total = contentWidth + 2;
        int left = Math.max(1, (total - label.length()) / 2);
        int right = Math.max(1, total - label.length() - left);
        return "+" + "-".repeat(left) + label + "-".repeat(right) + "+";
    }

    private static void printIndented(String prefix, String output) {
        for (String line : output.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if (!line.isBlank()) {
                System.out.printf("%s %s%n", prefix, line);
            }
        }
    }

    private static void writeSummaryReport(List<SummaryRow> summaryRows) {
        try {
            Files.createDirectories(SUMMARY_REPORT.getParent());
            StringBuilder csv = new StringBuilder("file,parse_status,semantic_status,symbols,tree,java,class,execution\n");
            for (SummaryRow row : summaryRows) {
                csv.append(row.toCsv()).append("\n");
            }
            Files.writeString(SUMMARY_REPORT, csv.toString(), StandardCharsets.UTF_8);
            System.out.printf("[SUMMARY] %s%n", BickSpecParseSupport.formatPathForDisplay(SUMMARY_REPORT));
        } catch (IOException exception) {
            System.err.println(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.ERROR,
                    "GEN04",
                    "Failed to write generation summary report: " + exception.getMessage(),
                    -1,
                    -1).formatted());
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

    private record SummaryRow(
            String file,
            String parseStatus,
            String semanticStatus,
            String symbols,
            String tree,
            String java,
            String classFile,
            String execution) {
        static SummaryRow generated(
                Path file,
                Path symbols,
                Path tree,
                Path java,
                Path classFile,
                String execution) {
            return new SummaryRow(
                    BickSpecParseSupport.formatPathForDisplay(file),
                    "OK",
                    "OK",
                    BickSpecParseSupport.formatPathForDisplay(symbols),
                    tree == null ? "not generated" : BickSpecParseSupport.formatPathForDisplay(tree),
                    BickSpecParseSupport.formatPathForDisplay(java),
                    BickSpecParseSupport.formatPathForDisplay(classFile),
                    execution);
        }

        static SummaryRow failed(
                Path file,
                String parseStatus,
                String semanticStatus,
                String symbols,
                String tree,
                String java,
                String classFile,
                String execution) {
            return new SummaryRow(
                    BickSpecParseSupport.formatPathForDisplay(file),
                    parseStatus,
                    semanticStatus,
                    symbols,
                    tree,
                    java,
                    classFile,
                    execution);
        }

        String toCsv() {
            return csv(file) + ","
                    + csv(parseStatus) + ","
                    + csv(semanticStatus) + ","
                    + csv(symbols) + ","
                    + csv(tree) + ","
                    + csv(java) + ","
                    + csv(classFile) + ","
                    + csv(execution);
        }

        private static String csv(String value) {
            if (value == null) {
                return "";
            }
            boolean quoted = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
            String escaped = value.replace("\"", "\"\"");
            return quoted ? "\"" + escaped + "\"" : escaped;
        }
    }

    private record ExecutionResult(boolean success, String stdout, String stderr, boolean capturedOutput) {
    }
}
