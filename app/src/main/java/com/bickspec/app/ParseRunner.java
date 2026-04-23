package com.bickspec.app;

import com.bickspec.grammar.BickSpecLexer;
import com.bickspec.grammar.BickSpecParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;

public final class ParseRunner {
    private ParseRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.ParseRunner <path-to-file-or-directory>");
            System.exit(1);
        }

        Path inputPath = Path.of(args[0]);
        List<Path> files = resolveInputFiles(inputPath);
        boolean hasFailure = false;

        for (Path file : files) {
            System.out.printf("==== %s ====%n", formatPathForDisplay(file));
            ParseResult result = parseFile(file);
            if (result.success()) {
                System.out.println("PARSE OK");
                System.out.println("Parse tree:");
                System.out.println(result.parseTree());
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

    private static List<Path> resolveInputFiles(Path inputPath) {
        if (Files.isRegularFile(inputPath)) {
            if (!inputPath.toString().toLowerCase().endsWith(".bks")) {
                System.err.printf("Input file is not a .bks file: %s%n", inputPath);
                System.exit(1);
            }
            return List.of(inputPath);
        }

        if (!Files.isDirectory(inputPath)) {
            System.err.printf("Input path does not exist: %s%n", inputPath);
            System.exit(1);
        }

        try (Stream<Path> stream = Files.list(inputPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".bks"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            System.err.printf("Failed to list directory: %s%n", exception.getMessage());
            System.exit(1);
            return List.of();
        }
    }

    private static ParseResult parseFile(Path sourceFile) {
        try {
            SyntaxErrorCollector lexicalErrors = new SyntaxErrorCollector();
            SyntaxErrorCollector syntaxErrors = new SyntaxErrorCollector();

            BickSpecLexer lexer = new BickSpecLexer(CharStreams.fromPath(sourceFile));
            lexer.removeErrorListeners();
            lexer.addErrorListener(lexicalErrors);

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            BickSpecParser parser = new BickSpecParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(syntaxErrors);

            ParseTree tree = parser.program();
            if (lexicalErrors.hasErrors()) {
                return ParseResult.failure("lexical", lexicalErrors.firstMessage());
            }
            if (syntaxErrors.hasErrors()) {
                return ParseResult.failure("syntax", syntaxErrors.firstMessage());
            }
            return ParseResult.success(tree.toStringTree(parser));
        } catch (IOException exception) {
            return ParseResult.failure("io", "Failed to read input file: " + exception.getMessage());
        }
    }

    private static String formatPathForDisplay(Path path) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        Path displayPath = absolutePath;
        if (absolutePath.startsWith(currentDirectory)) {
            displayPath = currentDirectory.relativize(absolutePath);
        }
        return displayPath.toString().replace('\\', '/');
    }

    private record ParseResult(boolean success, String parseTree, String errorType, String message) {
        static ParseResult success(String parseTree) {
            return new ParseResult(true, parseTree, null, null);
        }

        static ParseResult failure(String errorType, String message) {
            return new ParseResult(false, null, errorType, message);
        }
    }

    private static final class SyntaxErrorCollector extends BaseErrorListener {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String message,
                RecognitionException exception) {
            messages.add(String.format("line %d:%d %s", line, charPositionInLine, message));
        }

        boolean hasErrors() {
            return !messages.isEmpty();
        }

        String firstMessage() {
            return messages.get(0);
        }
    }
}
