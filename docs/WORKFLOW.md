# WORKFLOW.md

## Mandatory TDD Rule

For every behavior-changing task:

1. Analyze requirements.
2. Write unit tests and integration tests first.
3. Run tests and confirm RED.
4. Implement the minimum production code.
5. Run tests and confirm GREEN.
6. Refactor only after GREEN.
7. If refactoring changes behavior, return to RED.
8. Update documentation only when behavior, API, architecture, or rule changes.

Do not implement production code before RED is confirmed.
Do not skip integration tests unless the task is documentation-only or explicitly non-runtime.

## Verification Commands

Before completing implementation, run the narrowest relevant checks first.

Examples:

- Unit test for one module
- Integration test for affected adapter/repository/API
- Full build only when the change crosses module boundaries

If a command fails:
- report the failing command,
- summarize the failure,
- fix only failures caused by the current change.

Skipping steps is not allowed.

---

## 1. Requirement Analysis

Before writing code:

- Read relevant files and architecture first.
- Understand the exact requirement.
- Identify constraints from `RULE.md`.
- Identify affected modules and boundaries.
- State ambiguities and assumptions explicitly.

Output:
- Requirement summary
- Constraints
- Affected components
- Assumptions/questions

Do not implement immediately.

---

## 2. Test Design

Before implementation:

Design:
- Unit tests
- Integration tests

Requirements:
- Tests must reflect actual requirements.
- At least one new or updated test must fail before implementation begins.
- Define expected behavior explicitly, including concrete inputs and expected outputs or observable side effects.
- Cover success and failure cases.

Avoid:
- Testing implementation details
- Fake assertions without behavioral meaning

---

## 3. RED

Write failing tests first.

Requirements:
- Verify tests actually fail for the intended reason.
- Failure must demonstrate missing behavior.
- Do not modify production code yet.

Goal:
- Confirm requirement is not implemented.

---

## 4. Implement

Implement the minimum code required to satisfy tests.

Rules:
- Minimal diff
- No speculative abstraction
- No unrelated refactoring
- Follow existing architecture and conventions

Avoid:
- Overengineering
- Premature optimization
- Unrequested features

---

## 5. GREEN

Run:
- Unit tests
- Integration tests
- Build/lint checks

Requirements:
- All defined unit tests, integration tests, and listed build/lint checks must pass.
- Verify no regression occurred.
- Verify behavior matches requirements and the expected behavior defined in Step 2.

Do not continue if tests are unstable or unclear.

---

## 6. Refactor

Refactor only after GREEN.

Allowed:
- Remove duplication
- Improve readability
- Simplify structure

Not allowed:
- Behavioral changes
- Architectural rewrites without request

After refactoring:
- Return to Step 3 if behavior changes
- Otherwise continue to Step 7

---

## 7. Documentation (Optional)

If needed:
- Update README
- Update architecture docs
- Update API specs
- Update examples

Do not create unnecessary documentation.

---

# Core Principles

- TDD is mandatory.
- RED → GREEN → REFACTOR must always be preserved.
- Unit tests and integration tests are both required.
- Never implement before failing tests exist.
- Never refactor before GREEN.
- Never skip verification.

### Reference
- [[Unit Test]]
- [[Integration Test]]