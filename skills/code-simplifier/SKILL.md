---
name: code-simplifier
description: Review implementation code for avoidable complexity and behavior-preserving simplification. Use during code review, requested readability refactors, or authorized code changes; focus on the specified or currently edited scope.
---

# Code Simplifier

Make business logic easier to read in sequence, with no change to its observable behavior. Follow the target project's conventions. This skill supplies code-quality judgment; the project's existing process controls requirements, branches, plans, commits, integration, and delivery.

## Scope and mode

- In a code review, inspect the requested scope and report only concrete, evidenced findings with their location, consequence, and a proportionate suggestion. Do not edit code merely because this skill was selected. If there is no meaningful finding, say so.
- During an authorized implementation or simplification, improve only code already in the task's scope. Automatic discovery never grants permission to refactor neighboring code or remove a product capability.
- Before proposing an abstraction, query, or deletion, read the relevant implementation, callers, data relationships, and tests or UI contracts. Distinguish known requirements from imagined future cases.

## Decisions that improve readability

- Keep short, single-use, sequential steps in the business flow when a helper would only force a reader to jump elsewhere. Extract when there is a specific benefit: actual reuse, isolating complicated logic, or making a transaction, permission, or other meaningful boundary explicit. A method need not have multiple callers to be justified.
- Avoid branches, configuration, services, tools, and database work for scenarios that the actual contract does not require. If options are genuinely fixed, an ordered immutable constant can express them clearly; dynamic data should stay dynamic. Do not turn the fixed-options example into a universal caching rule.
- Prefer names and straightforward control flow over clever compression. Fewer lines or methods are not the goal; preserve useful shared validation and do not fuse distinct responsibilities into a giant method.

## Behavior boundary

Preserve accepted requests, response contents and order, exceptions, authorization, transaction scope, persistence, external calls, and other side effects. Check the before/after path and run relevant verification when editing. If the desirable change would remove an existing option or alter a contract, identify it as a requirement change for the project's normal decision process; do not implement it under a simplification request alone.

For example, a Java/Spring `/filters` endpoint whose options are fixed might return an ordered `List.of(PERSON, PET)` rather than count rows for each option. First check the endpoint contract and callers: if `ALL` is already exposed, removing it changes behavior; if a shared permission check or `@Transactional` method defines a real boundary, retain it even if it is short. Conversely, a private helper called once that merely assembles two adjacent response fields may obscure the service flow and be clearer inline.
