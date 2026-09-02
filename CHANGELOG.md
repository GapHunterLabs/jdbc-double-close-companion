<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JDBC Double-Close Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- Real path-sensitive typestate analysis (AST-structural recursion,
  merging both branches of every `if`/`else`, `try`/`catch`, and loop)
  flagging a JDBC resource `.close()`/use reached with the resource
  already closed on some or every reaching path.

[Unreleased]: https://github.com/GapHunterLabs/jdbc-double-close-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/jdbc-double-close-companion/commits/0.1.0
