# RULE.md

Project-specific rules for implementation.

This file defines where detailed rules live and which rules must be checked before coding.

## Rule Sources

Before implementation (before writing or modifying code), refer to the relevant documents under:

- [[module]]
- `docs/rule/module/*.md`
- `docs/rule/implement/*.md`
- [[code_convention]]
- `docs/rule/architecture_guide.puml`

## Required Reading

Always check these first before any code changes:

- `docs/rule/architecture_guide.puml`
- [[docs/rule/module]]
- [[dependencies]]
- [[code_convention]]

## Module Rules

When modifying a module, read the matching rule:

- API module → [[api]]
- Application module → [[application]]
- Service module → [[service]]
- Model module → [[model]]
- Exception module → [[exception]]
- Infrastructure module → [[infrastructure]]
- JPA repository module → [[repository-jpa]]
- Redis repository module → [[repository-redis]]

## Implementation Rules

When implementing related features, read the matching guide:

- Login user id → [[@LoginUserId]]
- Base entity → [[BaseEntity]]
- Spring bean registration → [[Spring Bean]]
- Conditional bean DSL → [[BeanRegistrarConditionalDsl]]
- Business exception → [[BusinessException Handling]]
- Domain model → [[Domain Model]]
- Port-adapter pattern → [[Port-Adapter Pattern]]
- Internal contract → [[Runtime-Neutral Internal Contract]]
- Security configuration → [[Security Configuration]]
- Security policy → [[Security Policy Contribution]]
- JPA value object → [[VO to Embeddable Value in JPA]]
- Integration test → [[Integration Test]]
- Liquibase Rule → [[검]]

## Rule Priority

When rules conflict, follow this order:

1. User request
2. [[RULE]]
3. Specific rule document under `docs/rule/**`
4. [[WORKFLOW]]
5. [[AGENTS]]

## Enforcement

Do not implement before checking the relevant rule documents required by the files or features being changed.

If a requested change conflicts with these rules:
- stop implementation,
- explain the conflict,
- ask for direction.

## Code Quality Check

After code changes, use Jacoco coverage as a quality signal.

- Run `./gradlew jacocoRootReport` when coverage can be affected.
- Check `build/reports/jacoco/jacocoRootReport/html/index.html`.
- Branch coverage should stay at or above 85%.
