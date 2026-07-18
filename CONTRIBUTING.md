# Contributing to boxlore

Thank you for your interest in contributing to boxlore! We welcome contributions from everyone.

## Getting Started

1. **Fork the Repository**: Start by forking the `ashwkun/boxlore` repository to your own GitHub account.
2. **Clone the Repository**: Clone your fork locally (`git clone https://github.com/YOUR_USERNAME/boxlore.git`).
3. **Create a Branch**: Create a new branch for your feature or bug fix (`git checkout -b feature/your-feature-name` or `git checkout -b fix/your-bug-fix-name`).

## How to Contribute

### Reporting Bugs
If you find a bug in the source code or a mistake in the documentation, you can help us by submitting an issue to our [GitHub Repository](https://github.com/ashwkun/boxlore/issues). Please use the **Bug Report** template to provide as much detail as possible.

### Suggesting Enhancements
If you have an idea for a new feature or improvement, please submit an issue using the **Feature Request** template. Provide a clear description of the problem your feature solves and the proposed solution.

### Submitting Pull Requests
- Ensure that your code adheres to standard Kotlin/Android formatting conventions.
- If your PR introduces a new feature, include relevant documentation updates.
- Please detail your changes clearly in the PR description so we can review your work properly.
- Follow the PR template (Conventional Commit title, exactly one `user-impact-*` label).
- **Merge CI:** expensive checks do not run on every push. When the PR is ready to merge, add the `merge-ci` label, wait for Unit + Instrumented checks to pass, then merge. See `.github/PULL_REQUEST_TEMPLATE.md`.

## API & Proxy Repository

The API and proxy backend repository is tracked separately and is private due to security reasons. If you require changes to the API or want to request a new endpoint, please raise an issue on this repository using the **Feature Request** or **Bug Report** template, and it will be taken up.

## Development Setup

See the main [README.md](README.md) for instructions on setting up the boxlore development environment. 

We appreciate your effort in helping improve boxlore!
