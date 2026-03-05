# Expected Outputs (Phase I)

Phase I covers lexical analysis only.

The expected result for each `.bks` input is a token stream where each line has:

`TOKEN_NAME<TAB>'LEXEME'`

You can generate current outputs with:

```powershell
.\app\scripts\run_tests.ps1 -SaveOutput
```

By default this writes:

`testing/outputs/phase1_tokens.txt`

Use this generated output as the baseline for manual or scripted comparisons.
