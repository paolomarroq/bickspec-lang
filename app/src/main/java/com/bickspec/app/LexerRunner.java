package com.bickspec.app;

import com.bickspec.grammar.BickSpecLexer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

public final class LexerRunner {
    private LexerRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar app/target/bickspec-lexer-runner-1.0.0.jar <path-to-file.bks>");
            System.exit(1);
        }

        Path sourceFile = Path.of(args[0]);
        if (!Files.isRegularFile(sourceFile)) {
            System.err.printf("Input file does not exist: %s%n", sourceFile);
            System.exit(1);
        }

        try {
            BickSpecLexer lexer = new BickSpecLexer(CharStreams.fromPath(sourceFile));
            Token token;
            while ((token = lexer.nextToken()).getType() != Token.EOF) {
                String tokenName = lexer.getVocabulary().getSymbolicName(token.getType());
                if (tokenName == null) {
                    tokenName = lexer.getVocabulary().getDisplayName(token.getType());
                }
                System.out.printf("%s\t'%s'%n", tokenName, escapeLexeme(token.getText()));
            }
        } catch (IOException exception) {
            System.err.printf("Failed to read input file: %s%n", exception.getMessage());
            System.exit(1);
        }
    }

    private static String escapeLexeme(String lexeme) {
        return lexeme
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("'", "\\'");
    }
}
