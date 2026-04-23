package com.bickspec.app;

import java.nio.file.Path;
import java.util.List;

public final class ParseRunner {
    private ParseRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.ParseRunner <path-to-file-or-directory>");
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
}
