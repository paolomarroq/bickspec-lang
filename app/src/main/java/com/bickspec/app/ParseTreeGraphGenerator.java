package com.bickspec.app;

import com.bickspec.grammar.BickSpecParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public final class ParseTreeGraphGenerator {
    private static final Path TREE_DIRECTORY = Path.of("testing", "trees");

    private ParseTreeGraphGenerator() {
    }

    public static GraphResult generate(Path sourceFile, BickSpecParseSupport.ParseResult parseResult) {
        try {
            Files.createDirectories(TREE_DIRECTORY);
            String baseName = parseTreeBaseName(sourceFile);
            Path dotFile = TREE_DIRECTORY.resolve(baseName + ".dot");
            Path svgFile = TREE_DIRECTORY.resolve(baseName + ".svg");

            String dot = toDot(parseResult.tree(), parseResult.parser());
            Files.writeString(dotFile, dot, StandardCharsets.UTF_8);

            Path generatedSvg = null;
            String svgMessage = null;
            if (renderSvgFromDot(dotFile, svgFile)) {
                generatedSvg = svgFile;
            } else {
                svgMessage = "Warning: Graphviz SVG generation failed, DOT file still created.";
            }

            return new GraphResult(dotFile, generatedSvg, svgMessage);
        } catch (IOException exception) {
            System.err.printf("Failed to write parse tree graph: %s%n", exception.getMessage());
            System.exit(1);
            return new GraphResult(TREE_DIRECTORY, null, "SVG generation skipped.");
        }
    }

    private static String toDot(ParseTree tree, BickSpecParser parser) {
        DotWriter writer = new DotWriter(parser);
        writer.visit(tree);
        return writer.render();
    }

    private static String parseTreeBaseName(Path sourceFile) {
        String filename = sourceFile.getFileName().toString();
        String stem = filename.endsWith(".bks") ? filename.substring(0, filename.length() - 4) : filename;
        String sanitized = stem.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isBlank()) {
            sanitized = "BickSpecProgram";
        }
        return sanitized + "_ParseTree";
    }

    private static boolean renderSvgFromDot(Path dotFile, Path svgFile) {
        try {
            Process process = new ProcessBuilder(
                    "dot",
                    "-Tsvg",
                    dotFile.toString(),
                    "-o",
                    svgFile.toString())
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record GraphResult(Path dotFile, Path svgFile, String svgMessage) {
        public List<String> displayLines() {
            List<String> lines = new ArrayList<>();
            lines.add(BickSpecParseSupport.formatPathForDisplay(dotFile));
            if (svgFile != null) {
                lines.add(BickSpecParseSupport.formatPathForDisplay(svgFile));
            } else if (svgMessage != null) {
                lines.add(svgMessage);
            }
            return lines;
        }
    }

    private static final class DotWriter {
        private final Parser parser;
        private final StringBuilder nodes = new StringBuilder();
        private final StringBuilder edges = new StringBuilder();
        private int nextId;

        DotWriter(Parser parser) {
            this.parser = parser;
        }

        void visit(ParseTree tree) {
            writeNode(tree);
        }

        String render() {
            return "digraph ParseTree {\n"
                    + "  rankdir=TB;\n"
                    + "  node [shape=box, style=\"rounded,filled\", fillcolor=\"#f8fafc\", fontname=\"Consolas\"];\n"
                    + "  edge [color=\"#475569\"];\n"
                    + nodes
                    + edges
                    + "}\n";
        }

        private int writeNode(ParseTree tree) {
            int id = nextId++;
            nodes.append("  node").append(id)
                    .append(" [label=\"")
                    .append(escape(labelFor(tree)))
                    .append("\"];\n");

            for (int index = 0; index < tree.getChildCount(); index++) {
                int childId = writeNode(tree.getChild(index));
                edges.append("  node").append(id).append(" -> node").append(childId).append(";\n");
            }
            return id;
        }

        private String labelFor(ParseTree tree) {
            if (tree instanceof TerminalNode terminalNode) {
                return terminalNode.getSymbol().getText();
            }
            return parser.getRuleNames()[((org.antlr.v4.runtime.RuleContext) tree).getRuleIndex()];
        }

        private static String escape(String value) {
            return value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
        }
    }
}
