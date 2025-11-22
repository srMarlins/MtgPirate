# Contributing to MtgPirate

Thank you for your interest in contributing to MtgPirate! We welcome contributions from the community to help make this tool better for everyone.

## Code of Conduct

Please be respectful and considerate of others when contributing to this project.

## How to Contribute

### Reporting Bugs

If you find a bug, please open an issue on GitHub with the following information:
- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior vs. actual behavior
- Screenshots or logs if applicable
- Your operating system and version

### Suggesting Enhancements

We love hearing ideas for new features! Please open an issue to discuss your idea before implementing it to ensure it aligns with the project's goals.

### Pull Requests

1. **Fork the repository** and create your branch from `main`.
2. **Install dependencies** and ensure the project builds locally.
3. **Make your changes**, keeping the code style consistent with the existing codebase.
4. **Run tests** to ensure your changes don't break existing functionality.
5. **Run Detekt** (`./gradlew detekt`) to check for code quality issues.
6. **Submit a Pull Request** with a clear description of your changes.

## Development Setup

### Prerequisites
- JDK 17 or later
- IntelliJ IDEA (Community or Ultimate) with Kotlin plugin
- Xcode (for iOS development)

### Build Commands
- Run Desktop App: `./gradlew run`
- Run Tests: `./gradlew test`
- Run Detekt: `./gradlew detekt`

## Architecture Guidelines

This project uses a **Kotlin Multiplatform** architecture with **Compose Multiplatform** for UI.
- **Common Logic**: Place platform-agnostic code in `commonMain`.
- **State Management**: Use the MVI pattern (see `docs/MVI_ARCHITECTURE.md`).
- **UI**: Use Jetpack Compose for UI components.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
