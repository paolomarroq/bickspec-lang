package com.bickspec.app;

import com.bickspec.grammar.BickSpecBaseVisitor;
import com.bickspec.grammar.BickSpecParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Phase II semantic visitor used to demonstrate tree traversal and planned
 * semantic actions.
 *
 * <p>The visitor receives the ANTLR parse tree after lexical and syntax
 * validation have succeeded. It walks relevant grammar nodes and records a
 * deterministic trace of visits and actions, such as project entry, imports,
 * assignments, display/read statements, control flow, functions, money
 * literals, and time literals.</p>
 *
 * <p>Output is a list of trace strings consumed by {@link ParseRunner} and
 * {@link TranspileRunner}. The visitor intentionally does not enforce a full
 * symbol table, type system, unit compatibility, or financial semantics yet;
 * those are Phase III responsibilities.</p>
 */
public final class BickSpecSemanticVisitor extends BickSpecBaseVisitor<Void> {
    private static final Set<String> BUILTIN_FUNCTIONS = Set.of("NPV", "PAYBACK");

    private final List<String> trace = new ArrayList<>();
    private final List<CompilerDiagnostic> diagnostics = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();
    private String scope = "global";
    private int depth;

    public SemanticResult analyze(BickSpecParser.ProgramContext context) {
        visit(context);
        return new SemanticResult(diagnostics.isEmpty(), symbolTable, List.copyOf(trace), List.copyOf(diagnostics));
    }

    @Override
    public Void visitProgram(BickSpecParser.ProgramContext context) {
        for (BickSpecParser.ImportStmtContext importStmt : context.importStmt()) {
            visit(importStmt);
        }
        for (BickSpecParser.FxStmtContext fxStmt : context.fxStmt()) {
            visit(fxStmt);
        }
        for (BickSpecParser.FunctionDeclContext functionDecl : context.functionDecl()) {
            declareFunction(functionDecl);
        }
        for (BickSpecParser.FunctionDeclContext functionDecl : context.functionDecl()) {
            validateFunction(functionDecl);
        }
        visit(context.projectBlock());
        return null;
    }

    @Override
    public Void visitProjectBlock(BickSpecParser.ProjectBlockContext context) {
        visitLog("projectBlock", "entering project " + context.STRING_LITERAL().getText());
        actionLog("create project scope and validate executable statements");
        return visitNested(context);
    }

    @Override
    public Void visitImportStmt(BickSpecParser.ImportStmtContext context) {
        visitLog("importStmt", sourceFragment(context));
        String module = context.ID().getText();
        if (!symbolTable.declare(new SymbolInfo(module, "module", "global", line(context), true, "import"))) {
            error("SEM02", "Duplicate import/module declaration '" + module + "'", context.ID().getSymbol());
        }
        actionLog("record dependency request for library/module '" + module + "'");
        return null;
    }

    @Override
    public Void visitFxStmt(BickSpecParser.FxStmtContext context) {
        visitLog("fxStmt", sourceFragment(context));
        String currency = "FX_" + context.currency().getText();
        if (!symbolTable.declare(new SymbolInfo(currency, "number", "global", line(context), true, "exchange rate"))) {
            error("SEM02", "Duplicate declaration of exchange rate '" + currency + "'", context.currency().getStart());
        }
        actionLog("register exchange-rate constant for currency conversion support");
        return null;
    }

    @Override
    public Void visitAssignStmt(BickSpecParser.AssignStmtContext context) {
        visitLog("assignStmt", sourceFragment(context));
        String variableName = context.ID().getText();
        ValueType expressionType = expressionType(context.expr());
        if (expressionType == ValueType.STRING) {
            error("SEM03", "Cannot assign text expression to numeric variable '" + variableName + "'", context.ID().getSymbol());
        }
        if (symbolTable.lookup(variableName, scope) == null) {
            symbolTable.declare(new SymbolInfo(variableName, "number", scope, line(context), true, unitNote(context.expr())));
        }
        actionLog("register assignment and prepare Java variable emission");
        return null;
    }

    @Override
    public Void visitBatchAssignStmt(BickSpecParser.BatchAssignStmtContext context) {
        visitLog("batchAssignStmt", sourceFragment(context));
        Set<String> targets = new HashSet<>();
        for (TerminalNode id : context.idList().ID()) {
            String variableName = id.getText();
            if (!targets.add(variableName)) {
                error("SEM02", "Duplicate declaration of variable '" + variableName + "'", id.getSymbol());
                continue;
            }
            if (symbolTable.lookup(variableName, scope) == null) {
                symbolTable.declare(new SymbolInfo(variableName, "number", scope, id.getSymbol().getLine(), true, batchUnitNote(context)));
            }
        }
        for (BickSpecParser.ExprContext expression : context.exprList().expr()) {
            if (isBatchExternalPlaceholder(expression)) {
                String name = expression.getText();
                if (symbolTable.lookup(name, scope) == null) {
                    symbolTable.declare(new SymbolInfo(name, "number", scope, expression.getStart().getLine(), true, "external batch value"));
                }
                continue;
            }
            expressionType(expression);
        }
        actionLog("register grouped assignment targets and prepare expanded Java assignments");
        return null;
    }

    @Override
    public Void visitDisplayStmt(BickSpecParser.DisplayStmtContext context) {
        visitLog("displayStmt", sourceFragment(context));
        expressionType(context.expr());
        actionLog("translate output statement to Java System.out.println(...)");
        return null;
    }

    @Override
    public Void visitReadStmt(BickSpecParser.ReadStmtContext context) {
        visitLog("readStmt", "READ " + context.ID().getText());
        String variableName = context.ID().getText();
        if (symbolTable.lookup(variableName, scope) == null) {
            symbolTable.declare(new SymbolInfo(variableName, "number", scope, line(context), true, "read input"));
        }
        actionLog("prepare Java input read and assign value to variable '" + variableName + "'");
        return null;
    }

    @Override
    public Void visitIfStmt(BickSpecParser.IfStmtContext context) {
        visitLog("ifStmt", "IF " + sourceFragment(context.condition()) + " THEN ...");
        validateCondition(context.condition());
        actionLog("translate conditional structure to Java if statement");
        return visitNested(context);
    }

    @Override
    public Void visitWhileStmt(BickSpecParser.WhileStmtContext context) {
        visitLog("whileStmt", "WHILE " + sourceFragment(context.condition()) + " DO ...");
        validateCondition(context.condition());
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
    public Void visitExprStmt(BickSpecParser.ExprStmtContext context) {
        expressionType(context.expr());
        return null;
    }

    @Override
    public Void visitMoneyLiteral(BickSpecParser.MoneyLiteralContext context) {
        visitLog("moneyLiteral", sourceFragment(context));
        actionLog("capture numeric amount and currency metadata");
        return null;
    }

    @Override
    public Void visitTimeLiteral(BickSpecParser.TimeLiteralContext context) {
        visitLog("timeLiteral", sourceFragment(context));
        actionLog("capture duration value and time unit metadata");
        return null;
    }

    private void declareFunction(BickSpecParser.FunctionDeclContext context) {
        String functionName = context.ID().getText();
        if (!symbolTable.declare(new SymbolInfo(functionName, "function", "global", line(context), true, "returns number"))) {
            error("SEM02", "Duplicate declaration of function '" + functionName + "'", context.ID().getSymbol());
        }
    }

    private void validateFunction(BickSpecParser.FunctionDeclContext context) {
        visitLog("functionDecl", "FUNCTION " + context.ID().getText() + "(...)");
        String previousScope = scope;
        scope = "function:" + context.ID().getText();
        Set<String> parameters = new HashSet<>();
        if (context.paramList() != null) {
            for (TerminalNode parameter : context.paramList().ID()) {
                String parameterName = parameter.getText();
                if (!parameters.add(parameterName)) {
                    error("SEM02", "Duplicate declaration of parameter '" + parameterName + "'", parameter.getSymbol());
                } else {
                    symbolTable.declare(new SymbolInfo(parameterName, "number", scope, parameter.getSymbol().getLine(), true, "parameter"));
                }
            }
        }
        if (expressionType(context.expr()) == ValueType.STRING) {
            error("SEM03", "Function '" + context.ID().getText() + "' cannot return text", context.ID().getSymbol());
        }
        actionLog("register function signature and prepare Java method emission");
        scope = previousScope;
    }

    private void validateCondition(BickSpecParser.ConditionContext context) {
        if (expressionType(context.expr(0)) == ValueType.STRING || expressionType(context.expr(1)) == ValueType.STRING) {
            error("SEM03", "Condition operands must be numeric", context.getStart());
        }
    }

    private ValueType expressionType(BickSpecParser.ExprContext context) {
        return expressionType(context.logicOrExpr());
    }

    private ValueType expressionType(ParserRuleContext context) {
        ValueType result = ValueType.NUMBER;
        for (int index = 0; index < context.getChildCount(); index++) {
            if (context.getChild(index) instanceof BickSpecParser.PrimaryContext primary) {
                result = combine(result, primaryType(primary));
            } else if (context.getChild(index) instanceof ParserRuleContext nested) {
                result = combine(result, expressionType(nested));
            }
        }
        return result;
    }

    private ValueType primaryType(BickSpecParser.PrimaryContext context) {
        if (context.STRING_LITERAL() != null) {
            return ValueType.STRING;
        }
        if (context.ID() != null) {
            String identifier = context.ID().getText();
            if (context.functionCallSuffix() != null) {
                visitLog("functionCall", identifier + sourceFragment(context.functionCallSuffix()));
                validateFunctionCall(identifier, context);
                return ValueType.NUMBER;
            }
            SymbolInfo symbol = symbolTable.lookup(identifier, scope);
            if (symbol == null) {
                error("SEM01", "Variable '" + identifier + "' used before declaration", context.ID().getSymbol());
                return ValueType.UNKNOWN;
            }
            if (!symbol.initialized()) {
                error("SEM01", "Variable '" + identifier + "' used before initialization", context.ID().getSymbol());
                return ValueType.UNKNOWN;
            }
            return "function".equals(symbol.type()) || "module".equals(symbol.type()) ? ValueType.UNKNOWN : ValueType.NUMBER;
        }
        return context.expr() != null ? expressionType(context.expr()) : ValueType.NUMBER;
    }

    private void validateFunctionCall(String identifier, BickSpecParser.PrimaryContext context) {
        if (symbolTable.lookupInScope(identifier, "global") == null && !BUILTIN_FUNCTIONS.contains(identifier)) {
            error("SEM04", "Function '" + identifier + "' is not declared", context.ID().getSymbol());
        }
        if (context.functionCallSuffix().argList() != null) {
            for (BickSpecParser.ExprContext argument : context.functionCallSuffix().argList().expr()) {
                if (expressionType(argument) == ValueType.STRING) {
                    error("SEM03", "Function '" + identifier + "' expects numeric arguments", argument.getStart());
                }
            }
        }
        actionLog("resolve function call and validate argument expressions");
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

    private void error(String code, String message, Token token) {
        diagnostics.add(new CompilerDiagnostic(
                CompilerDiagnostic.Severity.ERROR,
                code,
                message,
                token.getLine(),
                token.getCharPositionInLine()));
    }

    private String indent() {
        return "  ".repeat(depth);
    }

    private static int line(ParserRuleContext context) {
        return context.getStart().getLine();
    }

    private static String unitNote(BickSpecParser.ExprContext context) {
        String text = context.getText();
        if (text.contains("USD")) {
            return "unit USD";
        }
        if (text.contains("GTQ")) {
            return "unit GTQ";
        }
        if (text.contains("EUR")) {
            return "unit EUR";
        }
        if (text.contains("year") || text.contains("month") || text.contains("quarter")
                || text.contains("week") || text.contains("day")) {
            return "time unit";
        }
        return "";
    }

    private static String batchUnitNote(BickSpecParser.BatchAssignStmtContext context) {
        if (context.currency() != null) {
            return "unit " + context.currency().getText();
        }
        if (context.timeUnit() != null) {
            return "unit " + context.timeUnit().getText();
        }
        return "";
    }

    private static boolean isBatchExternalPlaceholder(BickSpecParser.ExprContext context) {
        return context.getText().matches("V[0-9]+");
    }

    private static ValueType combine(ValueType left, ValueType right) {
        if (left == ValueType.STRING || right == ValueType.STRING) {
            return ValueType.STRING;
        }
        if (left == ValueType.UNKNOWN || right == ValueType.UNKNOWN) {
            return ValueType.UNKNOWN;
        }
        return ValueType.NUMBER;
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

    private enum ValueType {
        NUMBER,
        STRING,
        UNKNOWN
    }
}
