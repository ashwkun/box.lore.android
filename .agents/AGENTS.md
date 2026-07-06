# Custom Agent Rules for boxlore Android App

- **Global Ruleset Reference**:
  - You must always read, refer to, and strictly follow the global rules defined in `/Users/aswinc/.gemini/config/AGENTS.md` (the "Autonomous Agent Git & QA Ruleset") for all Git, credentials, and development workflows.

- **UI & Styling Constraints**:
  - **No Glassmorphism for Cards**: Card containers, panels, and card background composables in Jetpack Compose must strictly use solid Material 3 surface colors (e.g. `MaterialTheme.colorScheme.surfaceContainerHigh`). Do not apply transparency, alpha reductions (like `.copy(alpha = ...)`), or frosted-glass styling to card backgrounds. Card borders must also remain solid and follow standard Material 3 outline color tokens.
