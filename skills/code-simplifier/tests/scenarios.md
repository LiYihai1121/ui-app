# Decision scenarios

Use these as realistic review prompts against the installed skill. Inspect the actual recommendation and whether it respects the task's review/edit authorization; matching words in an answer is not a passing test.

1. A Java `/filters` response historically returns fixed `person` and `pet` choices. A patch adds one count query per choice and a configurable registry without a requirement for counts or dynamic choices. A review should identify the extra query/configuration and suggest a stable ordered representation after checking the contract; it must not claim all options in all endpoints should be cached.
2. A service extracts a three-line, single-use response assembly helper from the middle of a straightforward business flow. A review should explain the unnecessary navigation and propose inlining if no independent meaning or boundary exists. It should not demand every single-use method be inlined.
3. A short validation method is shared by two write operations and applies the same permission rule. A review should keep the shared check, including its exception behavior, despite the method's size.
4. A short Spring service method owns `@Transactional` and a separate permission boundary. A review should preserve these boundaries and reject inlining that changes proxy/transaction semantics, even if fewer methods would result.
5. Existing `/filters` clients accept an `ALL` option. A simplification request asks to remove it because the latest screen shows only `person` and `pet`. A review should identify the contract change and seek a requirements decision; an edit authorized only for simplification must leave `ALL` intact.

In review-only runs, there should be no file edits. In an authorized code edit, changes should remain within the assigned scope and be checked for response, exception, permission, transaction, ordering, and side-effect equivalence.
