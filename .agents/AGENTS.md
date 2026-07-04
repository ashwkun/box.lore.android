# Custom Agent Rules for Boxlore Android App

- **UI & Styling Constraints**:
  - **No Glassmorphism for Cards**: Card containers, panels, and card background composables in Jetpack Compose must strictly use solid Material 3 surface colors (e.g. `MaterialTheme.colorScheme.surfaceContainerHigh`). Do not apply transparency, alpha reductions (like `.copy(alpha = ...)`), or frosted-glass styling to card backgrounds. Card borders must also remain solid and follow standard Material 3 outline color tokens.
