# bickspec-lang
BickSpec is a finance-oriented structured programming language designed for economic engineering modeling, built with Java and ANTLR4 as part of a Compiler Design project.

## Phase 2: Java + ANTLR lexer runner

Build command (from repository root):

```bash
mvn -f app/pom.xml clean package
```

Run command (example using a provided test program):

```bash
java -jar app/target/bickspec-lexer-runner-1.0.0.jar testing/P1_HolaMundo.bks
```

If no file path argument is provided, the runner prints usage and exits with code `1`.
