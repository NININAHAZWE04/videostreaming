[🇫🇷 FR](CONTRIBUTING.md) | **🇬🇧 EN**


# Contributing to the Project

Thank you for your interest! All contributions are welcome.

## Types of Accepted Contributions

- 🐛 **Bug fixes** — corrections of incorrect behaviors
- ✨ **Features** — new features discussed in an issue beforehand
- 📚 **Documentation** — improvements, corrections, translations
- ♻️ **Refactoring** — code improvement without behavior change
- 🧪 **Tests** — adding unit or integration tests

## Process

1. **Open an issue** before starting any significant work
2. **Fork** the repository
3. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/my-improvement
   git checkout -b fix/bug-description
   git checkout -b docs/update-readme
   ```
4. **Implement** following the standards below
5. **Verify the build**:
   ```bash
   # Java Backend
   ./build.sh

   # Frontends
   cd web && npm run build
   cd admin && npm run build
   ```
6. **Open a Pull Request** with:
   - Clear description of the change
   - Screenshots if UI was modified
   - Reference to the related issue (`Closes #42`)

## Code Standards

### Java
- Java 21 — use Text Blocks, Records, Switch expressions
- No external dependencies — everything must compile with the JDK only + h2.jar
- Each new behavior → `AppLogger.info/warn/error` (not `System.out.println`)
- Secrets are never hardcoded → use `AppConfig`
- Tests: manually verify via API endpoints

### React / JavaScript
- Functional components with hooks
- Tailwind for styling, CSS variables (`var(--text)`, `var(--brand)`, etc.)
- `authStore.js` for everything related to authentication
- No additional libraries without prior discussion

### Git
- Commit messages in French or English, explicit:
  ```
  feat: add GET /api/auth/me endpoint
  fix: fix download token expiration
  docs: update ARCHITECTURE.md with SSE flow
  ```
- One commit = one coherent change
- No secrets or personal data in commits

## What Not to Commit

- `.env` (secrets file)
- `data/` (H2 database)
- `videos/` (video files)
- `node_modules/`
- `bin/*.class`
- JWT keys, passwords, tokens

## Bug Reports

Include in the issue:
- OS and versions (Java/Node/Docker)
- Command or action that reproduces the bug
- Expected vs. observed behavior
- Error logs (`docker compose logs backend`)

## Questions?

Open an issue of type **Question** or **Discussion**.
