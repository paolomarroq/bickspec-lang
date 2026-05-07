package com.bickspec.app;

import com.bickspec.grammar.BickSpecBaseVisitor;
import com.bickspec.grammar.BickSpecParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public final class BickSpecJavaTranslatorVisitor extends BickSpecBaseVisitor<Void> {
    private final String className;
    private final String sourceDisplayPath;
    private final List<String> classComments = new ArrayList<>();
    private final List<String> constants = new ArrayList<>();
    private final Set<String> fxCurrencies = new LinkedHashSet<>();
    private final List<String> methods = new ArrayList<>();
    private final StringBuilder mainBody = new StringBuilder();
    private final Set<String> declaredVariables = new LinkedHashSet<>();
    private final Set<String> declaredFunctions = new LinkedHashSet<>();
    private final Set<String> calledFunctions = new LinkedHashSet<>();
    private int indentLevel = 2;
    private int loopCounter;

    public BickSpecJavaTranslatorVisitor(String className, String sourceDisplayPath) {
        this.className = className;
        this.sourceDisplayPath = sourceDisplayPath;
    }

    public String translate(BickSpecParser.ProgramContext context) {
        visit(context);
        return renderJavaSource();
    }

    @Override
    public Void visitImportStmt(BickSpecParser.ImportStmtContext context) {
        String module = context.ID().getText();
        Path resolvedModule = resolveImportModule(module);
        if (resolvedModule != null) {
            classComments.add("BickSpec import '" + module + "' resolved at "
                    + BickSpecParseSupport.formatPathForDisplay(resolvedModule)
                    + "; linked functions should be copied into this translation unit when needed.");
        } else {
            classComments.add("BickSpec import '" + module
                    + "' has no local .bks module file; built-ins are provided by generated runtime helpers.");
        }
        return null;
    }

    @Override
    public Void visitFxStmt(BickSpecParser.FxStmtContext context) {
        String currency = context.currency().getText();
        fxCurrencies.add(currency);
        constants.add("private static final double FX_" + currency + " = "
                + renderNumber(context.numberLiteral()) + "; // 1 USD = FX_" + currency + " " + currency);
        return null;
    }

    @Override
    public Void visitFunctionDecl(BickSpecParser.FunctionDeclContext context) {
        String functionName = context.ID().getText();
        declaredFunctions.add(functionName);

        List<String> parameters = new ArrayList<>();
        if (context.paramList() != null) {
            for (TerminalNode parameter : context.paramList().ID()) {
                parameters.add("double " + parameter.getText());
            }
        }

        JavaExpression expression = renderExpr(context.expr());
        StringBuilder method = new StringBuilder();
        method.append("    private static double ")
                .append(functionName)
                .append("(")
                .append(String.join(", ", parameters))
                .append(") {\n");
        method.append("        return ").append(expression.code()).append(";\n");
        method.append("    }\n");
        methods.add(method.toString());
        return null;
    }

    @Override
    public Void visitProjectBlock(BickSpecParser.ProjectBlockContext context) {
        line("// Project " + context.STRING_LITERAL().getText());
        List<BickSpecParser.StmtContext> statements = context.stmt();
        for (int index = 0; index < statements.size(); index++) {
            BickSpecParser.StmtContext statement = statements.get(index);
            BickSpecParser.ReadStmtContext readStatement = statement.readStmt();
            if (readStatement != null
                    && index > 0
                    && statements.get(index - 1).displayStmt() != null
                    && isStringDisplay(statements.get(index - 1).displayStmt())) {
                appendRead(readStatement.ID().getText(), promptText(statements.get(index - 1).displayStmt()));
                continue;
            }
            if (statement.displayStmt() != null
                    && index + 1 < statements.size()
                    && statements.get(index + 1).readStmt() != null
                    && isStringDisplay(statement.displayStmt())) {
                continue;
            }
            visit(statement);
        }
        return null;
    }

    @Override
    public Void visitAssignStmt(BickSpecParser.AssignStmtContext context) {
        appendAssignment(context.ID().getText(), renderExpr(context.expr()));
        return null;
    }

    @Override
    public Void visitBatchAssignStmt(BickSpecParser.BatchAssignStmtContext context) {
        List<TerminalNode> ids = context.idList().ID();
        List<BickSpecParser.ExprContext> expressions = context.exprList().expr();
        List<JavaExpression> renderedExpressions = new ArrayList<>();
        String commonUnit = context.currency() != null ? context.currency().getText() : null;
        if (commonUnit == null && context.timeUnit() != null) {
            commonUnit = context.timeUnit().getText();
        }
        for (BickSpecParser.ExprContext expressionContext : expressions) {
            JavaExpression rendered = renderExpr(expressionContext);
            renderedExpressions.add(rendered);
            if (commonUnit == null && rendered.unit() != null) {
                commonUnit = rendered.unit();
            }
        }
        for (int index = 0; index < ids.size(); index++) {
            JavaExpression expression = index < renderedExpressions.size()
                    ? renderedExpressions.get(index)
                    : new JavaExpression("0.0", "TODO: missing grouped assignment expression");
            ensureExternalBatchPlaceholder(expression.code());
            if (commonUnit != null && isCurrency(commonUnit) && expression.unit() == null) {
                expression = new JavaExpression(
                        moneyExpression(expression.code(), commonUnit),
                        commonUnit);
            }
            if (commonUnit != null && !isCurrency(commonUnit) && expression.unit() == null) {
                expression = new JavaExpression(expression.code(), commonUnit);
            }
            appendAssignment(ids.get(index).getText(), expression);
        }
        return null;
    }

    @Override
    public Void visitDisplayStmt(BickSpecParser.DisplayStmtContext context) {
        String displayCurrency = context.currency() != null
                ? context.currency().getText()
                : displayCurrency(context.expr());
        JavaExpression expression = displayCurrency == null
                ? renderExpr(context.expr())
                : renderLogicOr(context.expr().logicOrExpr());
        String code = expression.code();
        if (displayCurrency != null) {
            code = "formatMoney(fromUsd(" + code + ", \"" + displayCurrency + "\"), \"" + displayCurrency + "\")";
        }
        line("System.out.println(" + code + ");" + metadataComment(expression.unit()));
        return null;
    }

    @Override
    public Void visitReadStmt(BickSpecParser.ReadStmtContext context) {
        appendRead(context.ID().getText(), context.ID().getText());
        return null;
    }

    @Override
    public Void visitIfStmt(BickSpecParser.IfStmtContext context) {
        line("if (" + renderCondition(context.condition()) + ") {");
        indentLevel++;
        boolean inThenBlock = false;
        for (int index = 0; index < context.getChildCount(); index++) {
            ParseTree child = context.getChild(index);
            String text = child.getText();
            if ("THEN".equals(text)) {
                inThenBlock = true;
                continue;
            }
            if ("ELSE".equals(text)) {
                indentLevel--;
                line("} else {");
                indentLevel++;
                continue;
            }
            if ("END".equals(text)) {
                break;
            }
            if (inThenBlock && child instanceof BickSpecParser.StmtContext statement) {
                visit(statement);
            }
        }
        indentLevel--;
        line("}");
        return null;
    }

    @Override
    public Void visitWhileStmt(BickSpecParser.WhileStmtContext context) {
        line("while (" + renderCondition(context.condition()) + ") {");
        indentLevel++;
        visitStatementsBetween(context, "DO", "END");
        indentLevel--;
        line("}");
        return null;
    }

    @Override
    public Void visitRepeatStmt(BickSpecParser.RepeatStmtContext context) {
        String loopVariable = "repeatIndex" + (++loopCounter);
        line("for (int " + loopVariable + " = 0; "
                + loopVariable + " < " + context.INT().getText() + "; "
                + loopVariable + "++) {");
        indentLevel++;
        visitStatementsBetween(context, "TIMES", "END");
        indentLevel--;
        line("}");
        return null;
    }

    private void visitStatementsBetween(ParserRuleContext context, String startToken, String endToken) {
        boolean inBlock = false;
        for (int index = 0; index < context.getChildCount(); index++) {
            ParseTree child = context.getChild(index);
            String text = child.getText();
            if (startToken.equals(text)) {
                inBlock = true;
                continue;
            }
            if (endToken.equals(text)) {
                return;
            }
            if (inBlock && child instanceof BickSpecParser.StmtContext statement) {
                visit(statement);
            }
        }
    }

    private void appendAssignment(String variableName, JavaExpression expression) {
        String comment = expression.comment() == null ? metadataComment(expression.unit()) : " // " + expression.comment();
        if (declaredVariables.add(variableName)) {
            line("double " + variableName + " = " + expression.code() + ";" + comment);
        } else {
            line(variableName + " = " + expression.code() + ";" + comment);
        }
    }

    private void appendRead(String variableName, String prompt) {
        if (declaredVariables.add(variableName)) {
            line("double " + variableName + " = readNumber(\"" + escapeJava(prompt) + "\");");
        } else {
            line(variableName + " = readNumber(\"" + escapeJava(prompt) + "\");");
        }
    }

    private void ensureExternalBatchPlaceholder(String expressionCode) {
        if (expressionCode.matches("V[0-9]+") && declaredVariables.add(expressionCode)) {
            line("double " + expressionCode + " = 0.0; // external batch placeholder");
        }
    }

    private String renderJavaSource() {
        StringBuilder source = new StringBuilder();
        source.append("// Auto-generated by BickSpec Compiler\n");
        source.append("// Source: ").append(sourceDisplayPath).append("\n");
        source.append("/*\n");
        source.append(" * BickSpec runtime notes:\n");
        source.append(" * - Money is stored internally in USD.\n");
        source.append(" * - GTQ and EUR values are converted to USD using FX declarations.\n");
        source.append(" * - DISPLAY expr in GTQ/EUR affects presentation only, not stored values.\n");
        source.append(" * - NPV() and PAYBACK() are built-in language functions implemented as Java helpers.\n");
        source.append(" */\n\n");
        source.append("import java.util.Scanner;\n\n");
        source.append("public class ").append(className).append(" {\n");
        source.append("    private static final Scanner INPUT = new Scanner(System.in);\n\n");

        for (String comment : classComments) {
            source.append("    // ").append(comment).append("\n");
        }
        appendDefaultFxConstants();

        for (String constant : constants) {
            source.append("    ").append(constant).append("\n");
        }
        if (!constants.isEmpty()) {
            source.append("\n");
        }

        for (String method : methods) {
            source.append(method).append("\n");
        }

        source.append("    public static void main(String[] args) {\n");
        source.append(mainBody);
        source.append("    }\n\n");
        appendStandardHelpers(source);
        source.append("}\n");
        return source.toString();
    }

    private void appendDefaultFxConstants() {
        if (!fxCurrencies.contains("GTQ")) {
            constants.add("private static final double FX_GTQ = 1.0; // default when source omits FX GTQ");
            fxCurrencies.add("GTQ");
        }
        if (!fxCurrencies.contains("EUR")) {
            constants.add("private static final double FX_EUR = 1.0; // default when source omits FX EUR");
            fxCurrencies.add("EUR");
        }
    }

    private static void appendStandardHelpers(StringBuilder source) {
        source.append("    private static double readNumber(String prompt) {\n");
        source.append("        System.out.print(prompt + \": \");\n");
        source.append("        if (INPUT.hasNextDouble()) {\n");
        source.append("            return INPUT.nextDouble();\n");
        source.append("        }\n");
        source.append("        INPUT.next();\n");
        source.append("        return 0.0;\n");
        source.append("    }\n\n");
        source.append("    private static double convert(double value, String unit) {\n");
        source.append("        if (isCurrency(unit)) {\n");
        source.append("            return toUsd(value, unit);\n");
        source.append("        }\n");
        source.append("        // Time units are represented as numeric values with metadata comments.\n");
        source.append("        return value;\n");
        source.append("    }\n\n");
        source.append("    private static boolean isCurrency(String unit) {\n");
        source.append("        return \"USD\".equals(unit) || \"GTQ\".equals(unit) || \"EUR\".equals(unit);\n");
        source.append("    }\n\n");
        source.append("    private static double toUsd(double amount, String currency) {\n");
        source.append("        return switch (currency) {\n");
        source.append("            case \"USD\" -> amount;\n");
        source.append("            case \"GTQ\" -> amount / FX_GTQ;\n");
        source.append("            case \"EUR\" -> amount / FX_EUR;\n");
        source.append("            default -> amount;\n");
        source.append("        };\n");
        source.append("    }\n\n");
        source.append("    private static double fromUsd(double amount, String currency) {\n");
        source.append("        return switch (currency) {\n");
        source.append("            case \"USD\" -> amount;\n");
        source.append("            case \"GTQ\" -> amount * FX_GTQ;\n");
        source.append("            case \"EUR\" -> amount * FX_EUR;\n");
        source.append("            default -> amount;\n");
        source.append("        };\n");
        source.append("    }\n\n");
        source.append("    private static String formatMoney(double value, String currency) {\n");
        source.append("        return String.format(\"%.2f %s\", value, currency);\n");
        source.append("    }\n\n");
        source.append("    /**\n");
        source.append("     * BickSpec built-in: NPV(rate, capex, cashflows...)\n");
        source.append("     * Computes net present value using CAPEX as initial investment\n");
        source.append("     * and future cash flows discounted by rate.\n");
        source.append("     */\n");
        source.append("    private static double NPV(double rate, double capex, double... cashflows) {\n");
        source.append("        double value = -capex;\n");
        source.append("        for (int index = 0; index < cashflows.length; index++) {\n");
        source.append("            value += cashflows[index] / Math.pow(1.0 + rate, index + 1);\n");
        source.append("        }\n");
        source.append("        return value;\n");
        source.append("    }\n\n");
        source.append("    /**\n");
        source.append("     * BickSpec built-in: PAYBACK(capex, cashflows...)\n");
        source.append("     * Computes the recovery period based on cumulative cash flows.\n");
        source.append("     */\n");
        source.append("    private static double PAYBACK(double capex, double... cashflows) {\n");
        source.append("        double recovered = 0.0;\n");
        source.append("        for (int index = 0; index < cashflows.length; index++) {\n");
        source.append("            double cashflow = cashflows[index];\n");
        source.append("            if (recovered + cashflow >= capex) {\n");
        source.append("                if (cashflow == 0.0) {\n");
        source.append("                    return index + 1;\n");
        source.append("                }\n");
        source.append("                return index + ((capex - recovered) / cashflow);\n");
        source.append("            }\n");
        source.append("            recovered += cashflow;\n");
        source.append("        }\n");
        source.append("        return -1.0;\n");
        source.append("    }\n");
    }

    private void line(String code) {
        mainBody.append("    ".repeat(indentLevel)).append(code).append("\n");
    }

    private String renderCondition(BickSpecParser.ConditionContext context) {
        return renderExpr(context.expr(0)).code() + " "
                + context.compOp().getText() + " "
                + renderExpr(context.expr(1)).code();
    }

    private JavaExpression renderExpr(BickSpecParser.ExprContext context) {
        JavaExpression expression = renderLogicOr(context.logicOrExpr());
        for (int index = 1; index < context.getChildCount(); index++) {
            String text = context.getChild(index).getText();
            if (("to".equals(text) || "in".equals(text)) && index + 1 < context.getChildCount()) {
                String unit = context.getChild(index + 1).getText();
                if ("in".equals(text) && isCurrency(unit)) {
                    expression = new JavaExpression("fromUsd(" + expression.code() + ", \"" + unit + "\")", unit);
                } else if (isCurrency(unit)) {
                    expression = new JavaExpression("toUsd(" + expression.code() + ", \"" + unit + "\")", "USD");
                } else {
                    expression = new JavaExpression("convert(" + expression.code() + ", \"" + unit + "\")", unit);
                }
                index++;
            }
        }
        return expression;
    }

    private JavaExpression renderLogicOr(BickSpecParser.LogicOrExprContext context) {
        return renderOperatorChain(context);
    }

    private JavaExpression renderLogicAnd(BickSpecParser.LogicAndExprContext context) {
        return renderOperatorChain(context);
    }

    private JavaExpression renderEquality(BickSpecParser.EqualityExprContext context) {
        return renderOperatorChain(context);
    }

    private JavaExpression renderRelational(BickSpecParser.RelationalExprContext context) {
        return renderOperatorChain(context);
    }

    private JavaExpression renderAdditive(BickSpecParser.AdditiveExprContext context) {
        return renderOperatorChain(context);
    }

    private JavaExpression renderMultiplicative(BickSpecParser.MultiplicativeExprContext context) {
        return renderOperatorChain(context);
    }

    private JavaExpression renderUnary(BickSpecParser.UnaryExprContext context) {
        if (context.getChildCount() == 2) {
            JavaExpression expression = renderPrimary(context.primary());
            return new JavaExpression(context.getChild(0).getText() + expression.code(), expression.unit());
        }
        return renderPrimary(context.primary());
    }

    private JavaExpression renderPrimary(BickSpecParser.PrimaryContext context) {
        if (context.moneyLiteral() != null) {
            return renderMoneyLiteral(context.moneyLiteral());
        }
        if (context.timeLiteral() != null) {
            return renderTimeLiteral(context.timeLiteral());
        }
        if (context.numberLiteral() != null) {
            return new JavaExpression(renderNumber(context.numberLiteral()), null);
        }
        if (context.STRING_LITERAL() != null) {
            return new JavaExpression(context.STRING_LITERAL().getText(), null);
        }
        if (context.ID() != null) {
            String identifier = context.ID().getText();
            if (context.functionCallSuffix() != null) {
                calledFunctions.add(identifier);
                String comment = isBuiltInFunction(identifier) ? "BickSpec built-in" : null;
                return new JavaExpression(identifier + renderFunctionCallSuffix(context.functionCallSuffix()), null, comment);
            }
            return new JavaExpression(identifier, null);
        }
        return new JavaExpression("(" + renderExpr(context.expr()).code() + ")", null);
    }

    private String renderFunctionCallSuffix(BickSpecParser.FunctionCallSuffixContext context) {
        if (context.argList() == null) {
            return "()";
        }
        List<String> arguments = new ArrayList<>();
        for (BickSpecParser.ExprContext argument : context.argList().expr()) {
            arguments.add(renderExpr(argument).code());
        }
        return "(" + String.join(", ", arguments) + ")";
    }

    private JavaExpression renderMoneyLiteral(BickSpecParser.MoneyLiteralContext context) {
        String currency = context.currency().getText();
        return new JavaExpression(moneyExpression(renderNumber(context.numberLiteral()), currency), currency);
    }

    private JavaExpression renderTimeLiteral(BickSpecParser.TimeLiteralContext context) {
        return new JavaExpression(context.INT().getText(), context.timeUnit().getText());
    }

    private String renderNumber(BickSpecParser.NumberLiteralContext context) {
        return context.getText();
    }

    private JavaExpression renderOperatorChain(ParserRuleContext context) {
        StringBuilder expression = new StringBuilder();
        String unit = null;
        String comment = null;
        for (int index = 0; index < context.getChildCount(); index++) {
            if (index > 0) {
                expression.append(' ');
            }
            JavaExpression childExpression = renderChild(context.getChild(index));
            expression.append(childExpression.code());
            if (childExpression.unit() != null) {
                unit = childExpression.unit();
            }
            if (childExpression.comment() != null) {
                comment = childExpression.comment();
            }
        }
        return new JavaExpression(expression.toString(), unit, comment);
    }

    private JavaExpression renderChild(ParseTree child) {
        if (child instanceof BickSpecParser.LogicOrExprContext context) {
            return renderLogicOr(context);
        }
        if (child instanceof BickSpecParser.LogicAndExprContext context) {
            return renderLogicAnd(context);
        }
        if (child instanceof BickSpecParser.EqualityExprContext context) {
            return renderEquality(context);
        }
        if (child instanceof BickSpecParser.RelationalExprContext context) {
            return renderRelational(context);
        }
        if (child instanceof BickSpecParser.AdditiveExprContext context) {
            return renderAdditive(context);
        }
        if (child instanceof BickSpecParser.MultiplicativeExprContext context) {
            return renderMultiplicative(context);
        }
        if (child instanceof BickSpecParser.UnaryExprContext context) {
            return renderUnary(context);
        }
        if (child instanceof BickSpecParser.PrimaryContext context) {
            return renderPrimary(context);
        }
        if (child instanceof TerminalNode terminal) {
            return new JavaExpression(mapOperator(terminal.getText()), null);
        }
        return new JavaExpression(child.getText(), null);
    }

    private static String mapOperator(String operator) {
        return switch (operator) {
            case "&&" -> "&&";
            case "||" -> "||";
            default -> operator;
        };
    }

    private static boolean isStringDisplay(BickSpecParser.DisplayStmtContext context) {
        return context.expr().getText().startsWith("\"");
    }

    private Path resolveImportModule(String module) {
        String filename = module + ".bks";
        Path sourcePath = Path.of(sourceDisplayPath);
        List<Path> candidates = new ArrayList<>();
        Path sourceParent = sourcePath.getParent();
        if (sourceParent != null) {
            candidates.add(sourceParent.resolve(filename));
            candidates.add(sourceParent.resolve("libraries").resolve(filename));
            candidates.add(sourceParent.resolve("modules").resolve(filename));
        }
        candidates.add(Path.of("testing", filename));
        candidates.add(Path.of("testing", "libraries", filename));
        candidates.add(Path.of("testing", "modules", filename));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String displayCurrency(BickSpecParser.ExprContext context) {
        for (int index = 1; index + 1 < context.getChildCount(); index++) {
            if ("in".equals(context.getChild(index).getText())) {
                String unit = context.getChild(index + 1).getText();
                if (isCurrency(unit)) {
                    return unit;
                }
            }
        }
        return null;
    }

    private static String promptText(BickSpecParser.DisplayStmtContext context) {
        String literal = context.expr().getText();
        if (literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        return literal.endsWith(":") ? literal.substring(0, literal.length() - 1) : literal;
    }

    private static String metadataComment(String unit) {
        if (unit == null || unit.isBlank()) {
            return "";
        }
        if (unit.startsWith("TODO:")) {
            return " // " + unit;
        }
        return " // \"" + unit + "\"";
    }

    private static String moneyExpression(String amount, String currency) {
        return "USD".equals(currency) ? amount : "toUsd(" + amount + ", \"" + currency + "\")";
    }

    private static boolean isCurrency(String unit) {
        return "USD".equals(unit) || "GTQ".equals(unit) || "EUR".equals(unit);
    }

    private static boolean isBuiltInFunction(String functionName) {
        return "NPV".equals(functionName) || "PAYBACK".equals(functionName);
    }

    private static String escapeJava(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record JavaExpression(String code, String unit, String comment) {
        JavaExpression(String code, String unit) {
            this(code, unit, null);
        }
    }
}
