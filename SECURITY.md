# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 3.1.x   | :white_check_mark: |
| < 3.1   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it responsibly:

1. **Do not** open a public GitHub issue for security vulnerabilities.
2. Send a detailed report to the maintainers via GitHub Security Advisories (private vulnerability reporting) or email.
3. Include the following information:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## Security Measures

- **Client**: All sensitive data (accessibility service state, rules, stats) stored locally; no credentials transmitted.
- **Server**: Bearer token authentication for write endpoints; input validation and rate limiting enabled.
- **Dependencies**: Version catalog locked; GitHub Dependabot enabled for dependency updates.
- **CI/CD**: All changes require passing CI checks (ktlint, unit tests, type checking) before merge.

## Best Practices

- Never commit secrets, keys, or credentials to the repository.
- Use `local.properties` for local signing configuration (already in `.gitignore`).
- Report security issues through private channels only.
