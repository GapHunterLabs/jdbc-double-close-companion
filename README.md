# JDBC Double-Close Companion

Flags a JDBC resource `.close()`/use reached with the resource already
closed on some or every path.

## Why it exists

CWE-675 (Duplicate Operations on Resource) / adjacent to CWE-415
(Double Free): closing a JDBC `Connection`/`Statement` twice can throw
`SQLException` on some drivers, or trigger undefined behavior. Coverity
and Infer (real enterprise SAST tools) use "typestate-guided static
analysis" for exactly this class of problem, confirming it's a real,
established technique -- neither is free or an inline IDE plugin. The
platform's own bundled "JDBC resource opened but not safely closed"
inspection covers LEAKS (never closing), a different bug entirely --
never double-close or use-after-close with real branch sensitivity.

## Why built this way

- **Real path-sensitive typestate analysis** -- every tracked resource
  variable's possible states (`open`, `closed`, or `open-or-closed`
  depending on which path was taken) are propagated through the
  method's own statement structure.
- **Branches are actually merged, not just walked in textual order** --
  both sides of every `if`/`else` are analyzed, and the resulting
  states are combined via set union (the same "meet over all paths"
  operation a real dataflow framework performs). This is the first
  mechanism in this catalog that's genuinely path-sensitive -- every
  other whole-project mechanism resolves a CALL GRAPH or a GRAMMAR,
  never branches within a single method's own control flow.
- **AST-structural recursion, not an explicit CFG graph** -- Java's
  structured control flow (no `goto`) doesn't need a literal
  node/edge graph for real path-sensitivity; recursing the statement
  tree and merging branch results achieves the same property with
  less machinery, stated explicitly rather than overclaiming graph
  construction that wasn't actually built.

## v0.1 scope — stated honestly, not exhaustively

- Only `Connection`/`Statement`/`PreparedStatement`/
  `CallableStatement`/`ResultSet` local variables.
- A `catch` block's starting state is conservatively approximated as
  the state BEFORE the `try` (an exception can occur at any point
  inside it) -- this can only make a catch-block finding less certain,
  never more.
- A loop body is analyzed for exactly one iteration merged with zero
  iterations -- never a true fixed point across multiple iterations.
- A method with more than 400 statements is skipped entirely.

## Usage

Open a Java method that closes (or uses) a JDBC resource variable a
second time, whether unconditionally or only on one branch -- the
second call site shows a warning.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
