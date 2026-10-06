<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JDBC Double-Close Companion Changelog

## [Unreleased]

### Added

- A description page for the inspection in **Settings | Editor |
  Inspections**, which showed "Under construction".

### Changed

- The rating prompt's local counter keeps one-way fingerprints of findings
  instead of their file paths, and deletes the list that earlier versions
  kept.
- `PRIVACY.md` describes the values the plugin keeps in the IDE's local
  settings.

## [0.1.1]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.1.0]

### Added

- Real path-sensitive typestate analysis (AST-structural recursion,
  merging both branches of every `if`/`else`, `try`/`catch`, and loop)
  flagging a JDBC resource `.close()`/use reached with the resource
  already closed on some or every reaching path.

[Unreleased]: https://github.com/GapHunterLabs/jdbc-double-close-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/jdbc-double-close-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/jdbc-double-close-companion/commits/0.1.0
