package com.bickspec.app;

import com.bickspec.grammar.BickSpecBaseVisitor;
import com.bickspec.grammar.BickSpecParser;
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
        classComments.add("TODO: import mapping is partial for BickSpec module '" + context.ID().getText() + "'.");
        return null;
    }

    @Override
    public Void visitFxStmt(BickSpecParser.FxStmtContext context) {
        String currency = context.currency().getText();
        constants.add("private static final double FX_" + currency + " = " + renderNumber(context.numberLiteral()) + ";");
        classComments.add("TODO: currency conversion runtime not yet implemented for FX " + currency + ".");
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
        for (int index = 0; index < ids.size(); index++) {
            JavaExpression expression = index < expressions.size()
                    ? renderExpr(expressions.get(index))
                    : new JavaExpression("0.0", "TODO: missing grouped assignment expression");
            appendAssignment(ids.get(index).getText(), expression);
        }
        return null;
    }

    @Override
    public Void visitDisplayStmt(BickSpecParser.DisplayStmtContext context) {
        JavaExpression expression = renderExpr(context.expr());
        String code = expression.code();
        if (context.currency() != null) {
            code = "formatCurrency(" + code + ", \"" + context.currency().getText() + "\")";
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
        if (declaredVariables.add(variableName)) {
            line("double " + variableName + " = " + expression.code() + ";" + metadataComment(expression.unit()));
        } else {
            line(variableName + " = " + expression.code() + ";" + metadataComment(expression.unit()));
        }
    }

    private void appendRead(String variableName, String prompt) {
        if (declaredVariables.add(variableName)) {
            line("double " + variableName + " = readNumber(\"" + escapeJava(prompt) + "\");");
        } else {
            line(variableName + " = readNumber(\"" + escapeJava(prompt) + "\");");
        }
    }

    private String renderJavaSource() {
        StringBuilder source = new StringBuilder();
        source.append("// Auto-generated by BickSpec TranspileRunner\n");
        source.append("// Source: ").append(sourceDisplayPath).append("\n");
        source.append("// Phase II: syntax-directed translation to Java\n");
        source.append("// NOTE: Some domain-specific runtime behavior is still pending implementation.\n\n");
        source.append("import java.util.Scanner;\n\n");
        source.append("public class ").append(className).append(" {\n");
        source.append("    private static final Scanner INPUT = new Scanner(System.in);\n\n");

        for (String comment : classComments) {
            source.append("    // ").append(comment).append("\n");
        }
        if (!classComments.isEmpty()) {
            source.append("    // TODO: dimensional/unit semantics preserved as metadata only.\n\n");
        }

        for (String constant : constants) {
            source.append("    ").append(constant).append("\n");
        }
        if (!constants.isEmpty()) {
            source.append("\n");
        }

        for (String functionName : calledFunctions) {
            if (!declaredFunctions.contains(functionName)) {
                appendBuiltinStub(source, functionName);
            }
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

    private static void appendBuiltinStub(StringBuilder source, String functionName) {
        if ("NPV".equals(functionName)) {
            source.append("    private static double NPV(double rate, double capex, double... cashflows) {\n");
            source.append("        // TODO: implement real financial runtime for NPV.\n");
            source.append("        return 0.0;\n");
            source.append("    }\n\n");
            return;
        }
        source.append("    private static double ").append(functionName).append("(double... values) {\n");
        source.append("        // TODO: implement real runtime for built-in/domain function '")
                .append(functionName)
                .append("'.\n");
        source.append("        return 0.0;\n");
        source.append("    }\n\n");
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
        source.append("        // TODO: implement currency/time conversion runtime helper.\n");
        source.append("        return value;\n");
        source.append("    }\n\n");
        source.append("    private static String formatCurrency(double value, String currency) {\n");
        source.append("        return value + \" \" + currency;\n");
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
                expression = new JavaExpression("convert(" + expression.code() + ", \"" + unit + "\")", unit);
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
                return new JavaExpression(identifier + renderFunctionCallSuffix(context.functionCallSuffix()), null);
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
        return new JavaExpression(renderNumber(context.numberLiteral()), context.currency().getText());
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
        for (int index = 0; index < context.getChildCount(); index++) {
            if (index > 0) {
                expression.append(' ');
            }
            JavaExpression childExpression = renderChild(context.getChild(index));
            expression.append(childExpression.code());
            if (childExpression.unit() != null) {
                unit = childExpression.unit();
            }
        }
        return new JavaExpression(expression.toString(), unit);
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

    private static String escapeJava(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record JavaExpression(String code, String unit) {
    }
}
