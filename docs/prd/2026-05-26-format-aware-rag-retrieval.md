# PRD: Format-Aware RAG Retrieval

## Problem Statement

EcomAgents currently treats uploaded knowledge files as plain text for RAG retrieval. This works for simple `.txt` and `.md` documents, but it is brittle for structured files such as `.json`, `.csv`, `.xlsx`, `.xml`, `.yaml`, and `.properties`.

Users can upload complete product or operational data, ask a question that should be answerable from the knowledge base, and still receive an answer saying that the knowledge base did not contain the information. The root cause is not necessarily missing data. The current retrieval path may split a structured record across fixed-size chunks, return only the beginning of a matching document, or truncate the retrieved context before the complete record reaches the model.

The user needs knowledge retrieval to respect file structure so the model receives complete, relevant units such as a JSON product object, CSV row with headers, Markdown section, Excel row, PDF page section, or log event.

## Solution

Build a format-aware RAG pipeline that parses each supported file type into semantic `KnowledgeUnit` records. Indexing, text fallback, vector retrieval, diagnostics, and model context assembly should operate on these units rather than raw whole-document strings.

The first implementation should solve the current JSON truncation problem, then progressively add better parsers for text, table, document, configuration, and log formats.

Supported formats in scope:

- `.txt`
- `.md`
- `.pdf`
- `.docx`
- `.xlsx`
- `.csv`
- `.json`
- `.xml`
- `.yaml`
- `.yml`
- `.properties`
- `.log`

## User Stories

1. As an agent user, I want JSON product data to be retrieved as complete product records, so that product attributes are not lost due to chunk truncation.
2. As an agent user, I want CSV rows to be retrieved with their headers, so that the model understands what each value means.
3. As an agent user, I want Excel rows to include sheet name and column headers, so that row data remains interpretable.
4. As an agent user, I want Markdown documents to be retrieved by section, so that answers include the relevant heading context.
5. As an agent user, I want TXT documents to retrieve the paragraph or nearby context that actually matched my query, so that the answer is grounded in the relevant part of the file.
6. As an agent user, I want PDF answers to cite page-level context, so that I can trace the answer back to the source.
7. As an agent user, I want DOCX content to preserve headings and tables, so that document structure is not flattened into confusing text.
8. As an agent user, I want YAML and XML records to be retrieved as structured entries, so that nested configuration or catalog data stays complete.
9. As an agent user, I want `.properties` files to retrieve full key/value entries, so that configuration answers do not omit the key or value.
10. As an agent user, I want `.log` files to retrieve whole error events and stack traces, so that troubleshooting answers are not missing the important lines.
11. As an agent user, I want retrieval to prefer exact IDs, names, keys, titles, and field matches before semantic matches, so that known identifiers work reliably.
12. As an agent user, I want the system to avoid saying "not found" when a matching structured record exists in the uploaded file, so that I can trust the knowledge base.
13. As an administrator, I want retrieval diagnostics to show which units were matched, so that I can debug bad answers without reading raw sensitive content in logs.
14. As a developer, I want a common parser interface for all file types, so that new formats can be added without rewriting retrieval logic.
15. As a developer, I want text fallback and vector search to use the same unit model, so that behavior is consistent when vector indexing is unavailable.
16. As a developer, I want source locations on units, so that UI and logs can show file name, page, sheet, row, JSON path, XML path, or heading path.
17. As a developer, I want context budgets to preserve complete high-value units where possible, so that truncation happens predictably and usefully.
18. As a developer, I want parser-specific tests, so that format handling can evolve safely.

## Implementation Decisions

- Introduce a `KnowledgeUnit` domain model as the internal retrieval unit.
- Each unit should include document ID, knowledge base ID, file name, file type, unit type, title, content, metadata, and source location.
- Define a `KnowledgeUnitParser` interface that converts a knowledge document into one or more units.
- Add parser implementations incrementally:
  - Plain text parser for `.txt`
  - Markdown section parser for `.md`
  - JSON object/array-element parser for `.json`
  - CSV row parser for `.csv`
  - Excel sheet-row parser for `.xlsx`
  - PDF page/section parser for `.pdf`
  - DOCX heading/paragraph/table parser for `.docx`
  - XML node parser for `.xml`
  - YAML node parser for `.yaml` and `.yml`
  - Properties key/value parser for `.properties`
  - Log event parser for `.log`
- Keep `knowledge_documents` as the authoritative source content store.
- Make the unit index rebuildable from `knowledge_documents`, matching the existing local SimpleKnowledge runtime-index approach.
- Vector indexing should index unit content, not arbitrary fixed-size document chunks.
- Text fallback should search unit titles, metadata, source location, and content.
- Retrieval should combine:
  - exact identifier/name/key/title matching
  - keyword and CJK n-gram matching
  - vector similarity
  - format-specific structural matching
- Context assembly should return complete units where possible.
- If a unit exceeds the context budget, truncation should be format-aware:
  - JSON/YAML/XML: preserve identifying fields and matched fields first
  - CSV/XLSX: preserve headers and matched row values
  - Markdown/DOCX/PDF: preserve heading/page context and matched paragraph
  - LOG: preserve error line and nearby stack/context lines
- Add source attribution to returned context, but do not expose raw internal parser details to end users.
- Preserve the current RAG timeout and fallback behavior, but make fallback unit-based.
- Reduce reliance on the model to infer missing context; retrieval should send complete enough units to answer directly.

## Suggested Implementation Phases

1. Build the `KnowledgeUnit` model and parser interface.
2. Migrate vector indexing and text fallback to `KnowledgeUnit`.
3. Implement JSON object-level retrieval first to fix current product-data truncation.
4. Implement Markdown and TXT unit parsers.
5. Implement CSV and XLSX row-based retrieval.
6. Implement PDF and DOCX parsers with source locations.
7. Implement XML, YAML/YML, properties, and log parsers.
8. Add retrieval diagnostics showing matched unit count, parser type, source locations, fallback status, elapsed time, and returned character count.

## Testing Decisions

- Tests should assert external retrieval behavior, not parser implementation details.
- Parser tests should verify that each supported file type produces stable `KnowledgeUnit` records with correct source locations.
- Retrieval tests should cover:
  - exact match by product ID/name/key/title
  - query terms located near the end of a large document
  - structured record returned complete enough to answer
  - text fallback when vector index is not ready
  - context budget truncation preserving matched fields
  - empty result behavior when no unit matches
- Existing `KnowledgeBaseServiceTest` is prior art for focused service-level tests.
- Existing `RetrieveKnowledgeToolTest` is prior art for tool exposure tests.
- Add parser-specific unit tests near the backend service/parser package.
- Add integration-style service tests for JSON, CSV, Markdown, and log retrieval before expanding to heavier PDF/DOCX/XLSX dependencies.

## Out of Scope

- Replacing the authoritative `knowledge_documents` table.
- Building a persistent external vector database in this PRD.
- Full UI redesign of knowledge management.
- Full document preview or source viewer.
- Guaranteed perfect semantic ranking across all formats in the first iteration.
- Multi-tenant or permission-model changes.

## Further Notes

- The immediate user pain is JSON product data being retrieved as incomplete fixed-size chunks. JSON object-level retrieval should be the first vertical slice.
- Current timeout logs show that large tool result payloads and repeated web searches can still exhaust the chat stream budget. Format-aware retrieval should keep context compact, complete, and source-grounded.
- The design should keep adding formats cheap: a new parser should not require changing chat orchestration, tool registration, or frontend SSE handling.
