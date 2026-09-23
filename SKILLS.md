# System Tools, MCP Servers, and Skills Reference

This document provides a comprehensive inventory and reference guide for all available skills,
built-in tools, subagents, and Model Context Protocol (MCP) servers configured in the environment.

---

## Table of Contents

1. [Available Skills](#1-available-skills)
2. [Subagents](#2-subagents)
3. [Core & Built-in Tools](#3-core--built-in-tools)
4. [MCP Servers & Tools](#4-mcp-servers--tools)
    - [IntelliJ IDEA MCP (`intellij-idea`)](#intellij-idea-mcp-intellij-idea)
    - [RustRover MCP (`rustrover`)](#rustrover-mcp-rustrover)
    - [Context7 Documentation MCP (`context7`)](#context7-documentation-mcp-context7)
    - [Filesystem MCP (`filesystem`)](#filesystem-mcp-filesystem)
    - [GitHub Grep MCP (`gh_grep`)](#github-grep-mcp-gh_grep)
    - [Playwright Browser MCP (`playwright`)](#playwright-browser-mcp-playwright)
    - [Generic MCP Resource Tools](#generic-mcp-resource-tools)
5. [Operational Best Practices & Directory Routing](#5-operational-best-practices--directory-routing)

---

## 1. Available Skills

| Skill Name           | Scope / Trigger                                                                                 | Description                                                                                                  |
|:---------------------|:------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------|
| `customize-opencode` | Opencode configuration (`opencode.json`, `opencode.jsonc`, `.opencode/`, `~/.config/opencode/`) | Used exclusively when modifying agent configs, subagents, skills, plugins, MCP servers, or permission rules. |

---

## 2. Subagents

The `task` tool can spawn autonomous subagents for specialized or parallel workflows:

| Subagent Type | Specialization                                                                 | Thoroughness Options / Use Cases                                                                                |
|:--------------|:-------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------|
| `explore`     | Fast codebase exploration, symbol tracking, and architecture discovery.        | `quick` (basic search), `medium` (moderate exploration), `very thorough` (exhaustive cross-directory analysis). |
| `general`     | Multi-step task execution, complex research, parallel execution of work units. | Autonomous problem solving, multi-file edits, and structured investigations.                                    |

---

## 3. Core & Built-in Tools

| Tool        | Parameters                                                     | Description                                                                                                       |
|:------------|:---------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------|
| `bash`      | `command`, `timeout`, `workdir`                                | Executes bash commands in a persistent shell session. Follows strict directory verification and safety protocols. |
| `read`      | `filePath`, `limit`, `offset`                                  | Reads file or directory contents with line number annotations (1-indexed). Supports images and PDFs.              |
| `write`     | `filePath`, `content`                                          | Writes complete text content to create new files. **DO NOT use to overwrite existing files**.                     |
| `edit`      | `filePath`, `oldString`, `newString`, `replaceAll`             | Performs exact string replacements in existing files (preferred for targeted, diff-like modifications).           |
| `glob`      | `pattern`, `path`                                              | Fast file path matching using glob patterns (e.g., `**/*.java`, `src/**/*.ts`).                                   |
| `grep`      | `pattern`, `path`, `include`                                   | Fast regex search across file contents with file inclusion filters.                                               |
| `task`      | `description`, `prompt`, `subagent_type`, `command`, `task_id` | Spawns or resumes autonomous subagents (`explore`, `general`).                                                    |
| `skill`     | `name`                                                         | Loads specialized instruction sets and workflows for matching tasks.                                              |
| `todowrite` | `todos` (`content`, `priority`, `status`)                      | Maintains structured task lists and tracks step-by-step progress.                                                 |
| `compress`  | `topic`, `content` (`startId`, `endId`, `summary`)             | Collapses completed conversation ranges into dense, high-fidelity technical summaries to manage context window.   |
| `question`  | `questions` (`question`, `header`, `options`, `multiple`)      | Prompts the user with structured choices or clarification questions.                                              |
| `webfetch`  | `url`, `format` (`markdown`/`text`/`html`), `timeout`          | Fetches and parses content from web URLs.                                                                         |

---

## 4. MCP Servers & Tools

### IntelliJ IDEA MCP (`intellij-idea`)

*Connected Remote MCP Server via `http://127.0.0.1:64342/stream` for deep IDE AST indexing,
compilation, refactoring, database tools, and runtime debugging.*

#### A. Code Search & Semantic Navigation

- `intellij-idea_search_symbol`: Semantic lookup of classes, methods, and fields by identifier
  fragments.
- `intellij-idea_search_text`: Fast text search with 1-based match coordinates.
- `intellij-idea_search_regex`: Regular expression search with exact match coordinates.
- `intellij-idea_search_file`: Search files by project-relative glob patterns.
- `intellij-idea_get_symbol_info`: Retrieves quick documentation, signatures, and declarations for
  symbols at specific coordinates (`line`, `column`).
- `intellij-idea_analyze_calls`: Builds IDE Call Hierarchy trees (`INCOMING_CALLS` /
  `OUTGOING_CALLS`).
- `intellij-idea_list_directory_tree`: Generates structured tree representations of directories.

#### B. Editor & Refactoring Operations

- `intellij-idea_open_file_in_editor`: Opens specified files in active IDE editor tabs.
- `intellij-idea_get_all_open_file_paths`: Lists open and active editor tab paths.
- `intellij-idea_read_file`: Reads files, including `.class` decompilation and JAR/JRT archive
  entries (`jar!/`, `jrt://`).
- `intellij-idea_create_new_file`: Creates files with automatic parent directory scaffolding.
- `intellij-idea_apply_patch`: Applies unified diffs or Codex patches.
- `intellij-idea_reformat_file`: Applies project code style formatting rules.
- `intellij-idea_rename_refactoring`: Context-aware AST symbol rename refactoring across the entire
  project.

#### C. Code Quality, Build & Execution

- `intellij-idea_get_file_problems`: Runs IntelliJ inspections on a file to detect syntax errors and
  warnings.
- `intellij-idea_lint_files`: Batch inspection analysis across multiple files.
- `intellij-idea_build_project`: Triggers project or file-specific builds and returns compiler
  diagnostics.
- `intellij-idea_get_run_configurations`: Discovers project run configurations or runnable entry
  points in files.
- `intellij-idea_execute_run_configuration`: Executes run configurations with optional dynamic
  launch overrides.
- `intellij-idea_execute_terminal_command`: Runs commands within the IDE integrated terminal.
- `intellij-idea_execute_tool`: Dynamic IDE tool execution proxy.
- `intellij-idea_get_project_dependencies`: Retrieves all JPS/package manager project dependencies.
- `intellij-idea_get_project_modules`: Lists all modules and module types in the project.
- `intellij-idea_get_repositories`: Lists VCS/Git roots.
- `intellij-idea_git_status`: Returns porcelain-style index/worktree status for repositories.
- `intellij-idea_configure_python_interpreter`: Configures module Python interpreter.
- `intellij-idea_get_python_environment`: Inspects configured Python virtual environments and
  package managers.

#### D. Database & SQL Tools

- `intellij-idea_list_database_connections`: Lists configured database data sources.
- `intellij-idea_create_database_connection`: Configures new database connection with JDBC URL.
- `intellij-idea_edit_database_connection`: Updates database connection parameters.
- `intellij-idea_test_database_connection`: Checks database connectivity and reachability.
- `intellij-idea_list_database_schemas`: Lists schemas in a data source.
- `intellij-idea_introspect_schema`: Loads/refreshes schema metadata into local IDE model.
- `intellij-idea_list_schema_object_kinds`: Lists object types (tables, views, routines, etc.).
- `intellij-idea_list_schema_objects`: Enumerates database objects within a schema.
- `intellij-idea_get_database_object_description`: Detailed column, key, index, and constraint
  structure.
- `intellij-idea_preview_table_data`: Previews table rows formatted as CSV.
- `intellij-idea_execute_sql_query`: Executes arbitrary SQL queries and returns results.
- `intellij-idea_fetch_query_result`: Paginates through result sets using `resultSetId`.
- `intellij-idea_cancel_sql_query`: Cancels running queries by `sessionId`.
- `intellij-idea_list_recent_sql_queries`: Shows active and recent query history.

#### E. Interactive Debugger (JVM / XDebug)

- `intellij-idea_xdebug_start_debugger_session`: Launches debugging session from configuration or
  code location (`filePath` + `line`).
- `intellij-idea_xdebug_get_debugger_status`: Lists active debugger sessions.
- `intellij-idea_xdebug_control_session`: Controls execution (`STEP_INTO`, `STEP_OVER`, `STEP_OUT`,
  `RESUME`, `PAUSE`, `STOP`, `WAIT_FOR_PAUSE`, `DRAIN_EVENTS`).
- `intellij-idea_xdebug_get_threads`: Lists threads and current suspension state.
- `intellij-idea_xdebug_get_stack`: Retrieves call stack frames for threads.
- `intellij-idea_xdebug_get_frame_values`: Inspects local variables and fields at stack frames.
- `intellij-idea_xdebug_get_value_by_path`: Drills down into nested object properties and arrays.
- `intellij-idea_xdebug_set_variable`: Mutates runtime variable values in suspended frames.
- `intellij-idea_xdebug_evaluate_expression`: Evaluates arbitrary expressions within frame scope.
- `intellij-idea_xdebug_list_breakpoints`: Lists project breakpoints and their conditions/states.
- `intellij-idea_xdebug_set_breakpoint`: Sets line breakpoints, conditional breakpoints, and
  non-suspending logpoints/tracepoints (`suspendPolicy=NONE`).
- `intellij-idea_xdebug_remove_breakpoint`: Removes breakpoints by location, ID, or owner.
- `intellij-idea_xdebug_run_to_line`: Resumes execution until reaching the designated target line.

---

### RustRover MCP (`rustrover`)

*Connected Remote MCP Server via `http://127.0.0.1:64522/stream` for Rust workspace indexing, Cargo
compilation/diagnostics, semantic AST analysis, and refactoring.*

> **RUST OPERATIONS RULE**
>
> For any inspection, search, edit, patch, refactor, lint, or build operation involving **Rust code**,
> **ALWAYS PRIORITIZE `rustrover` MCP TOOLS (`rustrover_*`)** over generic file tools or IntelliJ
> tools. RustRover provides deep rust-analyzer AST understanding, Cargo diagnostics, and Rustfmt
> support.

#### A. Code Search & Semantic Navigation

- `rustrover_search_symbol`: Semantic lookup of structs, traits, functions, enums, modules, and
  fields by identifier fragments.
- `rustrover_search_text`: Fast text search with 1-based match coordinates in the Rust project.
- `rustrover_search_regex`: Regular expression search with exact match coordinates across Rust
  files.
- `rustrover_search_file`: Search files by glob patterns relative to the Rust project root.
- `rustrover_get_symbol_info`: Retrieves quick documentation, signatures, and type definitions for
  Rust symbols at specific coordinates (`line`, `column`).
- `rustrover_analyze_calls`: Builds IDE Call Hierarchy trees (`INCOMING_CALLS` / `OUTGOING_CALLS`)
  for Rust functions and methods.
- `rustrover_list_directory_tree`: Generates structured tree representations of directories within
  the Rust workspace.

#### B. Editor & Refactoring Operations

- `rustrover_open_file_in_editor`: Opens specified files in active RustRover editor tabs.
- `rustrover_get_all_open_file_paths`: Lists open and active editor tab paths in RustRover.
- `rustrover_read_file`: Reads files, including decompiled symbols and dependency crate sources.
- `rustrover_create_new_file`: Creates files with automatic parent directory scaffolding in the Rust
  project.
- `rustrover_apply_patch`: Applies unified diffs or Codex patches to Rust files.
- `rustrover_reformat_file`: Applies Rustfmt / IDE code style formatting rules.
- `rustrover_rename_refactoring`: Context-aware AST symbol rename refactoring across the entire Rust
  workspace (updates references, macro invocations, trait implementations).

#### C. Code Quality, Cargo Build & Execution

- `rustrover_get_file_problems`: Runs RustRover inspections, rust-analyzer checks, and compiler
  diagnostics on a file to detect syntax errors and warnings.
- `rustrover_lint_files`: Batch inspection analysis across multiple Rust files.
- `rustrover_build_project`: Triggers Cargo / RustRover build and returns compiler diagnostics and
  errors.
- `rustrover_get_run_configurations`: Discovers Cargo run/test configurations or runnable entry
  points (`main`, `#[test]`) in files.
- `rustrover_execute_run_configuration`: Executes Cargo run/test configurations with optional
  dynamic launch overrides.
- `rustrover_execute_terminal_command`: Runs commands within the RustRover integrated terminal.
- `rustrover_execute_tool`: Dynamic IDE tool execution proxy.
- `rustrover_get_project_dependencies`: Retrieves all Cargo dependencies from `Cargo.toml` /
  workspace.
- `rustrover_get_project_modules`: Lists all modules and module types in the Rust workspace.
- `rustrover_get_repositories`: Lists VCS/Git roots for the Rust workspace.
- `rustrover_git_status`: Returns porcelain-style index/worktree status for the Rust repository.

#### D. Database & SQL Tools

- `rustrover_list_database_connections`: Lists configured database data sources in RustRover.
- `rustrover_create_database_connection`: Configures new database connection with JDBC URL.
- `rustrover_edit_database_connection`: Updates database connection parameters.
- `rustrover_test_database_connection`: Checks database connectivity and reachability.
- `rustrover_list_database_schemas`: Lists schemas in a data source.
- `rustrover_introspect_schema`: Loads/refreshes schema metadata into local IDE model.
- `rustrover_list_schema_object_kinds`: Lists object types (tables, views, routines, etc.).
- `rustrover_list_schema_objects`: Enumerates database objects within a schema.
- `rustrover_get_database_object_description`: Detailed column, key, index, and constraint
  structure.
- `rustrover_preview_table_data`: Previews table rows formatted as CSV.
- `rustrover_execute_sql_query`: Executes arbitrary SQL queries and returns results.
- `rustrover_fetch_query_result`: Paginates through result sets using `resultSetId`.
- `rustrover_cancel_sql_query`: Cancels running queries by `sessionId`.
- `rustrover_list_recent_sql_queries`: Shows active and recent query history.

---

### Context7 Documentation MCP (`context7`)

*Specialized MCP server for retrieving current, authoritative API documentation and code examples.*

| Tool                          | Parameters             | Description                                                                                               |
|:------------------------------|:-----------------------|:----------------------------------------------------------------------------------------------------------|
| `context7_resolve-library-id` | `libraryName`, `query` | Resolves a library name to a Context7 ID (`/org/project`), providing snippet counts and benchmark scores. |
| `context7_query-docs`         | `libraryId`, `query`   | Queries documentation and real-world code examples for specific concepts within the resolved library.     |

---

### Filesystem MCP (`filesystem`)

*Direct filesystem operations and metadata inspection within allowed directories.*

| Tool                                   | Description                                                                      |
|:---------------------------------------|:---------------------------------------------------------------------------------|
| `filesystem_read_text_file`            | Reads file content with optional `head` and `tail` line limits.                  |
| `filesystem_read_file`                 | Legacy file reading tool (use `read_text_file`).                                 |
| `filesystem_read_media_file`           | Returns base64-encoded file data and MIME type (images/audio/resources).         |
| `filesystem_read_multiple_files`       | Reads multiple file paths simultaneously in a single batch.                      |
| `filesystem_write_file`                | Writes/overwrites complete text content to file.                                 |
| `filesystem_edit_file`                 | Performs line-based edits and returns git diff results.                          |
| `filesystem_create_directory`          | Creates single or nested directories recursively.                                |
| `filesystem_list_directory`            | Lists directory items tagged with `[FILE]` or `[DIR]`.                           |
| `filesystem_list_directory_with_sizes` | Detailed directory listing with file sizes and sorting options (`name`, `size`). |
| `filesystem_directory_tree`            | Generates recursive JSON tree structures of directory trees.                     |
| `filesystem_move_file`                 | Moves or renames files/directories.                                              |
| `filesystem_search_files`              | Searches recursively using glob patterns with exclude support.                   |
| `filesystem_get_file_info`             | Inspects metadata (file size, permissions, timestamps, type).                    |
| `filesystem_list_allowed_directories`  | Returns allowed root paths accessible to the filesystem server.                  |

---

### GitHub Grep MCP (`gh_grep`)

*Searches real-world implementations across public GitHub repositories.*

| Tool                   | Parameters                                                                       | Description                                                                                                           |
|:-----------------------|:---------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------|
| `gh_grep_searchGitHub` | `query`, `language`, `repo`, `path`, `useRegexp`, `matchCase`, `matchWholeWords` | Searches literal code patterns or regexes across millions of public GitHub repositories for idiomatic usage examples. |

---

### Playwright Browser MCP (`playwright`)

*End-to-end headless browser automation, UI testing, accessibility tree inspection, and
network/console monitoring.*

#### Navigation & Inspection

- `playwright_browser_navigate`: Navigates to a URL.
- `playwright_browser_navigate_back`: Navigates back in history.
- `playwright_browser_snapshot`: Captures the accessibility tree snapshot (preferable over
  screenshots for actions).
- `playwright_browser_find`: Searches accessibility tree for text or regex with contextual node
  references.
- `playwright_browser_take_screenshot`: Captures viewport or full-page screenshot (`css` or `device`
  pixel scale).
- `playwright_browser_tabs`: Manages browser tabs (`list`, `new`, `close`, `select`).

#### User Interaction & Input

- `playwright_browser_click`: Clicks or double-clicks elements with optional modifier keys.
- `playwright_browser_type`: Types text into inputs with optional slow character-by-character
  cadence.
- `playwright_browser_fill_form`: Batch fills form inputs (textbox, checkbox, radio, combobox,
  slider).
- `playwright_browser_select_option`: Selects option (s) in dropdown menus.
- `playwright_browser_hover`: Moves mouse over target element.
- `playwright_browser_drag`: Drags and drops between source and target elements.
- `playwright_browser_drop`: Drops external files or MIME-typed data onto elements.
- `playwright_browser_file_upload`: Uploads files through file choosers.
- `playwright_browser_press_key`: Presses single keyboard keys or character combos.
- `playwright_browser_handle_dialog`: Accepts or cancels JavaScript alerts/prompts/confirms.

#### Execution & Synchronization

- `playwright_browser_wait_for`: Waits for text appearance/disappearance or explicit time delays.
- `playwright_browser_evaluate`: Evaluates custom JavaScript in the context of the page or element.
- `playwright_browser_run_code_unsafe`: Executes arbitrary Node.js Playwright script snippets
  against the browser instance.
- `playwright_browser_close`: Closes the current browser page.

#### Observability

- `playwright_browser_console_messages`: Retrieves console logs (`error`, `warning`, `info`,
  `debug`).
- `playwright_browser_network_requests`: Lists network requests captured since page load.
- `playwright_browser_network_request`: Returns detailed headers and payload bodies of specific
  network requests.

---

### Generic MCP Resource Tools

| Tool                          | Parameters      | Description                                                                  |
|:------------------------------|:----------------|:-----------------------------------------------------------------------------|
| `list_mcp_resources`          | `server`        | Lists static or dynamic context resources provided by connected MCP servers. |
| `read_mcp_resource`           | `server`, `uri` | Reads content from a specific MCP resource URI.                              |
| `list_mcp_resource_templates` | `server`        | Lists parameterized URI templates for MCP resources.                         |

---

## 5. Operational Best Practices & Directory Routing

1. **Incremental / Diff-Style Edits (STRICT NO FULL-FILE OVERWRITE RULE)**:
    - **MANDATORY**: **NEVER overwrite entire existing files** (avoid using `write` or whole-file
      replacement tools on existing source files).
    - **Always prioritize targeted, diff-like modification approaches**:
        - Use `edit` for exact, minimal string and block replacements.
        - Use IDE patch tools (`rustrover_apply_patch`, `intellij-idea_apply_patch`) or
          `filesystem_edit_file` for unified diffs and multi-hunk updates.
        - Reserve `write` / `create_new_file` / `rustrover_create_new_file` /
          `intellij-idea_create_new_file` exclusively for creating newly introduced files.
2. **Rust Operations (HIGH PRIORITY)**:
    - **MANDATORY**: For Rust code modifications, reading, refactoring, building, linting, and
      inspecting, **always prioritize `rustrover` MCP tools (`rustrover_*`)**.
    - Use `rustrover_build_project`, `rustrover_get_file_problems`, or `rustrover_lint_files` after
      modifying Rust code to verify compilation and lint diagnostics.
    - Use `rustrover_rename_refactoring` for renaming structs, traits, functions, or fields in Rust
      code to guarantee AST-level cross-crate consistency.
3. **Java / JVM & Root Project Operations**:
    - For Java, Gradle, Fabric, NeoForge, and root project operations, prefer `intellij-idea` MCP
      tools (`intellij-idea_*`) for accurate AST navigation, inspections, compilation, and JVM
      debugging.
4. **Path Formatting**: Always provide absolute paths (e.g.,
   `/mnt/d/Github/source/repos/NetBridge/...`) for core file operations.
5. **Library Lookup**: For library documentation, use `context7_resolve-library-id` followed by
   `context7_query-docs` rather than assuming older APIs.
6. **Context Management**: Use `compress` periodically on finalized conversation segments to keep
   the context window focused and responsive.
