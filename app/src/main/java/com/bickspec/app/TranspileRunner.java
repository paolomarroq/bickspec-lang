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
                System.out.println("PARSE OK");
                System.out.println("Parse tree:");
                System.out.println(result.parseTree());
                System.out.println("Semantic visit trace:");
                result.semanticTrace().forEach(System.out::println);
                ParseTreeGraphGenerator.GraphResult graphResult = ParseTreeGraphGenerator.generate(file, result);
                System.out.println("Parse tree graph:");
                graphResult.displayLines().forEach(System.out::println);
                Path generatedFile = transpile(file, result);
                System.out.println("Generated Java file:");
                System.out.println(BickSpecParseSupport.formatPathForDisplay(generatedFile));
            } else {
                hasFailure = true;
                System.out.println("PARSE FAILED");
                System.out.printf("Error type: %s%n", result.errorType());
                System.out.printf("Message: %s%n", result.message());
            }
        }

        if (hasFailure) {
            System.exit(1);
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
            System.err.printf("Failed to write generated Java file: %s%n", exception.getMessage());
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
