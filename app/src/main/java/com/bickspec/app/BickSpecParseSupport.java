package com.bickspec.app;

import com.bickspec.grammar.BickSpecLexer;
import com.bickspec.grammar.BickSpecParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;

public final class BickSpecParseSupport {
    private BickSpecParseSupport() {
    }

    public static List<Path> resolveInputFiles(Path inputPath) {
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
                    .sorted(BickSpecParseSupport::compareTestPath)
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            System.err.printf("Failed to list directory: %s%n", exception.getMessage());
            System.exit(1);
            return List.of();
        }
    }

    public static ParseResult parseFile(Path sourceFile) {
        try {
            SyntaxErrorCollector lexicalErrors = new SyntaxErrorCollector("LEX01");
            SyntaxErrorCollector syntaxErrors = new SyntaxErrorCollector("SYN01");

            BickSpecLexer lexer = new BickSpecLexer(CharStreams.fromPath(sourceFile, StandardCharsets.UTF_8));
            lexer.removeErrorListeners();
            lexer.addErrorListener(lexicalErrors);

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            BickSpecParser parser = new BickSpecParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(syntaxErrors);

            ParseTree tree = parser.program();
            if (lexicalErrors.hasErrors()) {
                return ParseResult.failure("lexical", lexicalErrors.firstDiagnostic());
            }
            if (syntaxErrors.hasErrors()) {
                return ParseResult.failure("syntax", syntaxErrors.firstDiagnostic());
            }

            BickSpecSemanticVisitor semanticVisitor = new BickSpecSemanticVisitor();
            SemanticResult semanticResult = semanticVisitor.analyze((BickSpecParser.ProgramContext) tree);
            return ParseResult.success(tree, parser, tree.toStringTree(parser), semanticResult);
        } catch (IOException exception) {
            return ParseResult.failure(
                    "io",
                    new CompilerDiagnostic(
                            CompilerDiagnostic.Severity.ERROR,
                            "GEN01",
                            "Failed to read input file: " + exception.getMessage(),
                            -1,
                            -1));
        }
    }

    private static int compareTestPath(Path left, Path right) {
        String leftName = left.getFileName().toString();
        String rightName = right.getFileName().toString();
        int leftNumber = leadingTestNumber(leftName);
        int rightNumber = leadingTestNumber(rightName);
        if (leftNumber >= 0 && rightNumber >= 0 && leftNumber != rightNumber) {
            return Integer.compare(leftNumber, rightNumber);
        }
        return leftName.compareToIgnoreCase(rightName);
    }

    private static int leadingTestNumber(String filename) {
        if (!filename.startsWith("P")) {
            return -1;
        }
        int index = 1;
        while (index < filename.length() && Character.isDigit(filename.charAt(index))) {
            index++;
        }
        if (index == 1) {
            return -1;
        }
        try {
            return Integer.parseInt(filename.substring(1, index));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public static String formatPathForDisplay(Path path) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        Path displayPath = absolutePath;
        if (absolutePath.startsWith(currentDirectory)) {
            displayPath = currentDirectory.relativize(absolutePath);
        }
        return displayPath.toString().replace('\\', '/');
    }

    public record ParseResult(
            boolean success,
            ParseTree tree,
            BickSpecParser parser,
            String parseTree,
            SemanticResult semanticResult,
            String errorType,
            CompilerDiagnostic diagnostic) {
        static ParseResult success(
                ParseTree tree,
                BickSpecParser parser,
                String parseTree,
                SemanticResult semanticResult) {
            return new ParseResult(true, tree, parser, parseTree, semanticResult, null, null);
        }

        static ParseResult failure(String errorType, CompilerDiagnostic diagnostic) {
            return new ParseResult(false, null, null, null, null, errorType, diagnostic);
        }
    }

    private static final class SyntaxErrorCollector extends BaseErrorListener {
        private final String code;
        private final List<String> messages = new ArrayList<>();
        private final List<CompilerDiagnostic> diagnostics = new ArrayList<>();

        SyntaxErrorCollector(String code) {
            this.code = code;
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String message,
                RecognitionException exception) {
            messages.add(String.format("line %d:%d %s", line, charPositionInLine, message));
            diagnostics.add(new CompilerDiagnostic(
                    CompilerDiagnostic.Severity.ERROR,
                    code,
                    normalizeMessage(message),
                    line,
                    charPositionInLine));
        }

        boolean hasErrors() {
            return !messages.isEmpty();
        }

        String firstMessage() {
            return messages.get(0);
        }

        CompilerDiagnostic firstDiagnostic() {
            return diagnostics.get(0);
        }

        private static String normalizeMessage(String message) {
            if (message == null || message.isBlank()) {
                return "Compiler error";
            }
            return Character.toUpperCase(message.charAt(0)) + message.substring(1);
        }
    }
}
