package com.bickspec.app;

import com.bickspec.grammar.BickSpecBaseVisitor;
import com.bickspec.grammar.BickSpecParser;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

public final class BickSpecSemanticVisitor extends BickSpecBaseVisitor<Void> {
    private final List<String> trace = new ArrayList<>();
    private int depth;

    public List<String> getTrace() {
        return List.copyOf(trace);
    }

    @Override
    public Void visitProjectBlock(BickSpecParser.ProjectBlockContext context) {
        visitLog("projectBlock", "entering project " + context.STRING_LITERAL().getText());
        actionLog("create Java class entry point for project block");
        return visitNested(context);
    }

    @Override
    public Void visitImportStmt(BickSpecParser.ImportStmtContext context) {
        visitLog("importStmt", sourceFragment(context));
        actionLog("record dependency request for library/module '" + context.ID().getText() + "'");
        return visitNested(context);
    }

    @Override
    public Void visitFxStmt(BickSpecParser.FxStmtContext context) {
        visitLog("fxStmt", sourceFragment(context));
        actionLog("register exchange-rate constant for currency conversion support");
        return visitNested(context);
    }

    @Override
    public Void visitFunctionDecl(BickSpecParser.FunctionDeclContext context) {
        visitLog("functionDecl", "FUNCTION " + context.ID().getText() + "(...)");
        actionLog("register function signature and prepare Java method emission");
        return visitNested(context);
    }

    @Override
    public Void visitAssignStmt(BickSpecParser.AssignStmtContext context) {
        visitLog("assignStmt", sourceFragment(context));
        actionLog("register assignment and prepare Java variable emission");
        return visitNested(context);
    }

    @Override
    public Void visitBatchAssignStmt(BickSpecParser.BatchAssignStmtContext context) {
        visitLog("batchAssignStmt", sourceFragment(context));
        actionLog("register grouped assignment targets and prepare expanded Java assignments");
        return visitNested(context);
    }

    @Override
    public Void visitDisplayStmt(BickSpecParser.DisplayStmtContext context) {
        visitLog("displayStmt", sourceFragment(context));
        actionLog("translate output statement to Java System.out.println(...)");
        return visitNested(context);
    }

    @Override
    public Void visitReadStmt(BickSpecParser.ReadStmtContext context) {
        visitLog("readStmt", "READ " + context.ID().getText());
        actionLog("prepare Java input read and assign value to variable '" + context.ID().getText() + "'");
        return visitNested(context);
    }

    @Override
    public Void visitIfStmt(BickSpecParser.IfStmtContext context) {
        visitLog("ifStmt", "IF " + sourceFragment(context.condition()) + " THEN ...");
        actionLog("translate conditional structure to Java if statement");
        return visitNested(context);
    }

    @Override
    public Void visitWhileStmt(BickSpecParser.WhileStmtContext context) {
        visitLog("whileStmt", "WHILE " + sourceFragment(context.condition()) + " DO ...");
        actionLog("translate loop condition and body to Java while statement");
        return visitNested(context);
    }

    @Override
    public Void visitRepeatStmt(BickSpecParser.RepeatStmtContext context) {
        visitLog("repeatStmt", "REPEAT " + context.INT().getText() + " TIMES ...");
        actionLog("translate counted repetition to Java for loop");
        return visitNested(context);
    }

    @Override
    public Void visitPrimary(BickSpecParser.PrimaryContext context) {
        if (context.ID() != null && context.functionCallSuffix() != null) {
            visitLog("functionCall", context.ID().getText() + sourceFragment(context.functionCallSuffix()));
            actionLog("resolve function call and prepare Java invocation expression");
        }
        return visitNested(context);
    }

    @Override
    public Void visitFunctionCallSuffix(BickSpecParser.FunctionCallSuffixContext context) {
        visitLog("functionCallSuffix", sourceFragment(context));
        actionLog("collect argument expressions for function invocation");
        return visitNested(context);
    }

    @Override
    public Void visitMoneyLiteral(BickSpecParser.MoneyLiteralContext context) {
        visitLog("moneyLiteral", sourceFragment(context));
        actionLog("capture numeric amount and currency metadata");
        return visitNested(context);
    }

    @Override
    public Void visitTimeLiteral(BickSpecParser.TimeLiteralContext context) {
        visitLog("timeLiteral", sourceFragment(context));
        actionLog("capture duration value and time unit metadata");
        return visitNested(context);
    }

    private Void visitNested(ParserRuleContext context) {
        depth++;
        visitChildren(context);
        depth--;
        return null;
    }

    private void visitLog(String nodeName, String detail) {
        trace.add(indent() + "[VISIT] " + nodeName + " -> " + detail);
    }

    private void actionLog(String action) {
        trace.add(indent() + "[ACTION] " + action);
    }

    private String indent() {
        return "  ".repeat(depth);
    }

    private static String sourceFragment(ParserRuleContext context) {
        int startIndex = context.getStart().getStartIndex();
        int stopIndex = context.getStop().getStopIndex();
        return context.getStart()
                .getInputStream()
                .getText(Interval.of(startIndex, stopIndex))
                .trim()
                .replaceAll("\\s+", " ");
    }
}
