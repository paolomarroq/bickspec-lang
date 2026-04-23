package com.bickspec.app;

import com.bickspec.grammar.BickSpecLexer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

public final class LexerRunner {
    private LexerRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar app/target/bickspec-lexer-runner-1.0.0.jar <path-to-file-or-directory>");
            System.exit(1);
        }

        Path inputPath = Path.of(args[0]);
        if (Files.isRegularFile(inputPath)) {
            if (!inputPath.toString().toLowerCase().endsWith(".bks")) {
                System.err.printf("Input file is not a .bks file: %s%n", inputPath);
                System.exit(1);
            }
            runLexerForFile(inputPath);
            return;
        }

        if (!Files.isDirectory(inputPath)) {
            System.err.printf("Input path does not exist: %s%n", inputPath);
            System.exit(1);
        }

        List<Path> files = listBksFiles(inputPath);
        for (Path file : files) {
            System.out.printf("==== %s ====%n", formatPathForDisplay(file));
            runLexerForFile(file);
        }
    }

    private static List<Path> listBksFiles(Path directory) {
        try {
            try (Stream<Path> stream = Files.list(directory)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".bks"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                        .collect(Collectors.toList());
            }
        } catch (IOException exception) {
            System.err.printf("Failed to list directory: %s%n", exception.getMessage());
            System.exit(1);
            return List.of();
        }
    }

    private static void runLexerForFile(Path sourceFile) {
        try {
            BickSpecLexer lexer = new BickSpecLexer(CharStreams.fromPath(sourceFile, StandardCharsets.UTF_8));
            Token token;
            while ((token = lexer.nextToken()).getType() != Token.EOF) {
                String tokenName = lexer.getVocabulary().getSymbolicName(token.getType());
                if (tokenName == null) {
                    tokenName = lexer.getVocabulary().getDisplayName(token.getType());
                }
                System.out.printf("%s\t'%s'%n", tokenName, token.getText());
            }
        } catch (IOException exception) {
            System.err.printf("Failed to read input file: %s%n", exception.getMessage());
            System.exit(1);
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
}
