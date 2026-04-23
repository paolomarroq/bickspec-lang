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
    private final List<String> classComments = new ArrayList<>();
    private final List<String> constants = new ArrayList<>();
    private final List<String> methods = new ArrayList<>();
    private final StringBuilder mainBody = new StringBuilder();
    private final Set<String> declaredVariables = new LinkedHashSet<>();
    private final Set<String> declaredFunctions = new LinkedHashSet<>();
    private final Set<String> calledFunctions = new LinkedHashSet<>();
    private int indentLevel = 2;
    private int loopCounter;

    public BickSpecJavaTranslatorVisitor(String className) {
        this.className = className;
    }

    public String translate(BickSpecParser.ProgramContext context) {
        visit(context);
        return renderJavaSource();
    }

    @Override
    public Void visitImportStmt(BickSpecParser.ImportStmtContext context) {
        classComments.add("BickSpec import requested: " + context.ID().getText());
        return null;
    }

    @Override
    public Void visitFxStmt(BickSpecParser.FxStmtContext context) {
        String currency = context.currency().getText();
        constants.add("private static final double FX_" + currency + " = " + renderNumber(context.numberLiteral()) + ";");
        classComments.add("FX " + currency + " registered for future currency conversion support.");
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

        StringBuilder method = new StringBuilder();
        method.append("    private static double ")
                .append(functionName)
                .append("(")
                .append(String.join(", ", parameters))
                .append(") {\n");
        method.append("        return ").append(renderExpr(context.expr())).append(";\n");
        method.append("    }\n");
        methods.add(method.toString());
        return null;
    }

    @Override
    public Void visitProjectBlock(BickSpecParser.ProjectBlockContext context) {
        line("// Project " + context.STRING_LITERAL().getText());
        for (BickSpecParser.StmtContext statement : context.stmt()) {
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
            String expression = index < expressions.size()
                    ? renderExpr(expressions.get(index))
                    : "0.0 /* TODO: missing grouped assignment expression */";
            appendAssignment(ids.get(index).getText(), expression);
        }
        return null;
    }

    @Override
    public Void visitDisplayStmt(BickSpecParser.DisplayStmtContext context) {
        String expression = renderExpr(context.expr());
        if (context.currency() != null) {
            expression = "formatCurrency(" + expression + ", \"" + context.currency().getText() + "\")";
        }
        line("System.out.println(" + expression + ");");
        return null;
    }

    @Override
    public Void visitReadStmt(BickSpecParser.ReadStmtContext context) {
        String variableName = context.ID().getText();
        if (declaredVariables.add(variableName)) {
            line("double " + variableName + " = readNumber(\"" + variableName + "\");");
        } else {
            line(variableName + " = readNumber(\"" + variableName + "\");");
        }
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

    private void appendAssignment(String variableName, String expression) {
        if (declaredVariables.add(variableName)) {
            line("double " + variableName + " = " + expression + ";");
        } else {
            line(variableName + " = " + expression + ";");
        }
    }

    private String renderJavaSource() {
        StringBuilder source = new StringBuilder();
        source.append("import java.util.Scanner;\n\n");
        source.append("public class ").append(className).append(" {\n");
        source.append("    private static final Scanner INPUT = new Scanner(System.in);\n\n");

        for (String comment : classComments) {
            source.append("    // ").append(comment).append("\n");
        }
        if (!classComments.isEmpty()) {
            source.append("\n");
        }

        for (String constant : constants) {
            source.append("    ").append(constant).append("\n");
        }
        if (!constants.isEmpty()) {
            source.append("\n");
        }

        for (String functionName : calledFunctions) {
            if (!declaredFunctions.contains(functionName)) {
                source.append("    // TODO: replace built-in/domain function stub with real runtime implementation.\n");
                source.append("    private static double ").append(functionName).append("(double... values) {\n");
                source.append("        return 0.0;\n");
                source.append("    }\n\n");
            }
        }

        for (String method : methods) {
            source.append(method).append("\n");
        }

        source.append("    public static void main(String[] args) {\n");
        source.append(mainBody);
        source.append("    }\n\n");
        source.append("    private static double readNumber(String name) {\n");
        source.append("        System.out.print(name + \": \");\n");
        source.append("        if (INPUT.hasNextDouble()) {\n");
        source.append("            return INPUT.nextDouble();\n");
        source.append("        }\n");
        source.append("        INPUT.next();\n");
        source.append("        return 0.0;\n");
        source.append("    }\n\n");
        source.append("    private static double convert(double value, String unit) {\n");
        source.append("        // TODO: translate currency/time conversion to runtime helper.\n");
        source.append("        return value;\n");
        source.append("    }\n\n");
        source.append("    private static String formatCurrency(double value, String currency) {\n");
        source.append("        return value + \" \" + currency;\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private void line(String code) {
        mainBody.append("    ".repeat(indentLevel)).append(code).append("\n");
    }

    private String renderCondition(BickSpecParser.ConditionContext context) {
        return renderExpr(context.expr(0)) + " " + context.compOp().getText() + " " + renderExpr(context.expr(1));
    }

    private String renderExpr(BickSpecParser.ExprContext context) {
        String expression = renderLogicOr(context.logicOrExpr());
        for (int index = 1; index < context.getChildCount(); index++) {
            String text = context.getChild(index).getText();
            if (("to".equals(text) || "in".equals(text)) && index + 1 < context.getChildCount()) {
                expression = "convert(" + expression + ", \"" + context.getChild(index + 1).getText() + "\")";
                index++;
            }
        }
        return expression;
    }

    private String renderLogicOr(BickSpecParser.LogicOrExprContext context) {
        return renderOperatorChain(context);
    }

    private String renderLogicAnd(BickSpecParser.LogicAndExprContext context) {
        return renderOperatorChain(context);
    }

    private String renderEquality(BickSpecParser.EqualityExprContext context) {
        return renderOperatorChain(context);
    }

    private String renderRelational(BickSpecParser.RelationalExprContext context) {
        return renderOperatorChain(context);
    }

    private String renderAdditive(BickSpecParser.AdditiveExprContext context) {
        return renderOperatorChain(context);
    }

    private String renderMultiplicative(BickSpecParser.MultiplicativeExprContext context) {
        return renderOperatorChain(context);
    }

    private String renderUnary(BickSpecParser.UnaryExprContext context) {
        if (context.getChildCount() == 2) {
            return context.getChild(0).getText() + renderPrimary(context.primary());
        }
        return renderPrimary(context.primary());
    }

    private String renderPrimary(BickSpecParser.PrimaryContext context) {
        if (context.moneyLiteral() != null) {
            return renderMoneyLiteral(context.moneyLiteral());
        }
        if (context.timeLiteral() != null) {
            return renderTimeLiteral(context.timeLiteral());
        }
        if (context.numberLiteral() != null) {
            return renderNumber(context.numberLiteral());
        }
        if (context.STRING_LITERAL() != null) {
            return context.STRING_LITERAL().getText();
        }
        if (context.ID() != null) {
            String identifier = context.ID().getText();
            if (context.functionCallSuffix() != null) {
                calledFunctions.add(identifier);
                return identifier + renderFunctionCallSuffix(context.functionCallSuffix());
            }
            return identifier;
        }
        return "(" + renderExpr(context.expr()) + ")";
    }

    private String renderFunctionCallSuffix(BickSpecParser.FunctionCallSuffixContext context) {
        if (context.argList() == null) {
            return "()";
        }
        List<String> arguments = new ArrayList<>();
        for (BickSpecParser.ExprContext argument : context.argList().expr()) {
            arguments.add(renderExpr(argument));
        }
        return "(" + String.join(", ", arguments) + ")";
    }

    private String renderMoneyLiteral(BickSpecParser.MoneyLiteralContext context) {
        return renderNumber(context.numberLiteral()) + " /* " + context.currency().getText() + " */";
    }

    private String renderTimeLiteral(BickSpecParser.TimeLiteralContext context) {
        return context.INT().getText() + " /* " + context.timeUnit().getText() + " */";
    }

    private String renderNumber(BickSpecParser.NumberLiteralContext context) {
        return context.getText();
    }

    private String renderOperatorChain(ParserRuleContext context) {
        StringBuilder expression = new StringBuilder();
        for (int index = 0; index < context.getChildCount(); index++) {
            if (index > 0) {
                expression.append(' ');
            }
            expression.append(renderChild(context.getChild(index)));
        }
        return expression.toString();
    }

    private String renderChild(ParseTree child) {
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
            return mapOperator(terminal.getText());
        }
        return child.getText();
    }

    private static String mapOperator(String operator) {
        return switch (operator) {
            case "&&" -> "&&";
            case "||" -> "||";
            default -> operator;
        };
    }
}
