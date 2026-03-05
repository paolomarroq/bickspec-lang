grammar BickSpec;
/* =========================
 *  Parser rules
 * ========================= */
program
  : (importStmt | fxStmt | functionDecl)* projectBlock EOF
  ;
importStmt
  : IMPORT ID
  ;
fxStmt
  : FX currency ASSIGN NUMBER
  ;
projectBlock
  : PROJECT STRING_LITERAL LBRACE stmt* RBRACE
  ;
stmt
  : assignStmt
  | displayStmt
  | readStmt
  | ifStmt
  | whileStmt
  | repeatStmt
  | exprStmt
  ;
assignStmt
  : ID ASSIGN expr
  ;
displayStmt
  : (DISPLAY | WRITE) expr (IN currency)?
  ;
readStmt
  : READ ID
  ;
ifStmt
  : IF condition THEN stmt* (ELSE stmt*)? END
  ;
whileStmt
  : WHILE condition DO stmt* END
  ;
repeatStmt
  : REPEAT INT TIMES stmt* END
  ;
exprStmt
  : expr
  ;
/* condition: inequalities & equality */
condition
  : expr compOp expr
  ;
compOp
  : GE | LE | EQ | NEQ | GT | LT
  ;
/* user functions: one-line expression */
functionDecl

  : FUNCTION ID LPAREN paramList? RPAREN ASSIGN expr
  ;
paramList
  : ID (COMMA ID)*
  ;
argList
  : expr (COMMA expr)*
  ;
/* expression with precedence (no left recursion) */
expr
  : logicOrExpr ( (IN currency) | (TO (currency | timeUnit)) )*
  ;
logicOrExpr
  : logicAndExpr (OR logicAndExpr)*
  ;
logicAndExpr
  : equalityExpr (AND equalityExpr)*
  ;
equalityExpr
  : relationalExpr ((EQ | NEQ) relationalExpr)*
  ;
relationalExpr
  : additiveExpr ((GT | LT | GE | LE) additiveExpr)*
  ;
additiveExpr
  : multiplicativeExpr ((PLUS | MINUS) multiplicativeExpr)*
  ;
multiplicativeExpr
  : unaryExpr ((STAR | SLASH) unaryExpr)*
  ;
unaryExpr
  : (NOT | MINUS)? primary
  ;
primary
  : moneyLiteral
  | timeLiteral
  | NUMBER
  | STRING_LITERAL
  | ID functionCallSuffix?
  | LPAREN expr RPAREN
  ;
functionCallSuffix
  : LPAREN argList? RPAREN
  ;
moneyLiteral
  : NUMBER currency
  ;
timeLiteral
  : INT timeUnit
  ;
currency
  : USD | GTQ | EUR
  ;
timeUnit
  : YEAR | MONTH | QUARTER | WEEK | DAY
  ;
/* =========================
 *  Lexer rules
 * ========================= */

/* Keywords */
IMPORT  : 'IMPORT';
PROJECT : 'PROJECT';
FX      : 'FX';
READ    : 'READ';
DISPLAY : 'DISPLAY';
WRITE   : 'WRITE';
IF      : 'IF';
THEN    : 'THEN';
ELSE    : 'ELSE';
WHILE   : 'WHILE';
DO      : 'DO';
REPEAT  : 'REPEAT';
TIMES   : 'TIMES';
END     : 'END';
FUNCTION: 'FUNCTION';
/* Conversions (lowercase for readability) */
TO      : 'to';
IN      : 'in';
/* Currencies */
USD : 'USD';
GTQ : 'GTQ';
EUR : 'EUR';
/* Time units (singular) */
YEAR    : 'year';
MONTH   : 'month';
QUARTER : 'quarter';
WEEK    : 'week';
DAY     : 'day';
/* Operators */
ASSIGN : ':=';
PLUS  : '+';
MINUS : '-';
STAR  : '*';
SLASH : '/';
GE  : '>=';
LE  : '<=';
EQ  : '==';
NEQ : '!=';
GT  : '>';
LT  : '<';
AND : '&&';
OR  : '||';
NOT : '!';
/* Punctuation */
LBRACE : '{';
RBRACE : '}';
LPAREN : '(';
RPAREN : ')';
COMMA  : ',';
/* Numbers */
INT
  : [0-9]+
  ;
FLOAT
  : [0-9]+ '.' [0-9]+
  ;
NUMBER
  : FLOAT
  | INT
  ;
/* Strings */
STRING_LITERAL
  : '"' ( '\\' . | ~["\\] )* '"'

  ;
/* Identifiers */
ID
  : [a-zA-Z_][a-zA-Z0-9_]*
  ;
/* Comments and whitespace */
LINE_COMMENT
  : '#' ~[\r\n]* -> skip
  ;
BLOCK_COMMENT
  : '/*' .*? '*/' -> skip
  ;
WS
  : [ \t\r\n]+ -> skip;