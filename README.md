# DB Script Executor

A Java-based utility for reliably executing SQL migration scripts against a database in a controlled, auditable, and safe manner — built primarily for PostgreSQL, with dialect support for MySQL, SQL Server, and Oracle.

**Package:** `deployment.sqlexecutor`
**Tech:** Java · Maven · JDBC (PostgreSQL JDBC Driver 42.7.3)
**Version:** 1.0.0

---

## Key Features

- Executes `.sql` files from a source directory in deterministic alphabetical order
- Prevents re-execution of scripts already applied successfully (idempotency guard)
- Detects and blocks unsafe SQL statements before they reach the database
- Full audit trail via a `schema_migration_history` table and timestamped log files
- Context-aware SQL statement splitter — correctly handles quoted strings, dollar-quoted blocks ($$...$$), and comments
- Per-statement execution metadata: timing, rows affected, statement type
- Color-coded console execution summary (`FileSummary`) with a spinner/loading animation on daemon threads
- Skip logic for common non-fatal errors (duplicate keys, already-exists conditions), with aggregated skip totals
- Transaction rollback visibility per file
- Multi-database dialect support via `DbDialect` (PostgreSQL, MySQL, SQL Server, Oracle)

---

## Project Structure

```
sqlexecutor/
├── src/main/java/deployment/sqlexecutor/
│   ├── DbScriptExecutor.java   # Main execution engine
│   ├── DbConfig.java           # Database configuration model
│   ├── AppSettings.java        # appsettings.json loader
│   ├── ExecutionLogger.java    # Timestamped log file writer
│   ├── FileMover.java          # Moves executed files to archive
│   └── ConsoleColor.java       # Console color formatting
├── target/
│   └── sqlexecutor-0.0.1-SNAPSHOT.jar
├── pom.xml
└── appsettings.json
```

---

## Configuration

Create an `appsettings.json` in the same directory as the executable:

```json
{
  "database": {
    "host": "127.0.0.1",
    "port": 5432,
    "name": "ghcm",
    "schema": "ghcmschema",
    "user": "ghcmuser",
    "password": "yourpassword",
    "sslmode": "disable"
  },
  "folders": {
    "sourceDir": "C:/SQL/Source",
    "targetDir": "C:/SQL/Archive"
  }
}
```

| Key | Section | Description |
|---|---|---|
| `host` | database | Server hostname or IP |
| `port` | database | Port number (default 5432) |
| `name` | database | Target database name |
| `schema` | database | Target schema (search_path is set to this) |
| `user` | database | Login/role used to authenticate |
| `password` | database | Password for the specified user |
| `sslmode` | database | `disable` for local, `require` for managed/cloud DBs |
| `sourceDir` | folders | Absolute path containing `.sql` files to execute |
| `targetDir` | folders | Absolute path where successfully executed files are archived |

> Use forward slashes (`/`) as the path separator, even on Windows.

---

## Usage — Manual / Console Mode

1. Locate the binary distribution folder and open `appsettings.json`.
2. Fill in all required database and folder values.
3. Ensure `sourceDir` contains the `.sql` files to execute.
4. Run the `.exe` (or jar) file.
5. Review the connection details and directory paths printed to console.
6. Confirm with `YES` when prompted to proceed.
7. On completion, successfully executed files move to `targetDir`, and a log file is written to `logs/`.

**Best practice:** prefix SQL filenames with a numeric sequence (`001_create_table.sql`, `002_insert_data.sql`) to explicitly control execution order.

---

## Execution Workflow

1. Read configuration from `appsettings.json`
2. Validate the target schema exists (`information_schema.schemata`)
3. Set `search_path` to the configured schema
4. Create `schema_migration_history` if it doesn't exist
5. Confirm active schema matches configuration
6. Display connection/environment metadata
7. Prompt for user confirmation
8. Discover `.sql` files recursively, filter, sort alphabetically
9. Skip files already recorded as `SUCCESS`
10. Split each file into statements (context-aware parser)
11. Skip transaction control statements (`COMMIT`, `ROLLBACK`, `BEGIN`, `START TRANSACTION`) — the tool manages its own boundaries
12. Validate each statement against unsafe patterns
13. Execute each file within a savepoint
14. Roll back and log `SKIPPED` for skippable errors; roll back and mark `FAILED` for all others
15. Commit and record `SUCCESS` on full-file success
16. Move successfully executed files to the archive directory

---

## Safety Validation (Blocked Statements)

The following patterns cause immediate failure of the entire file:

| Pattern | Reason |
|---|---|
| `UPDATE` without `WHERE` | Prevents unintended full-table updates |
| `DELETE` without `WHERE` | Prevents unintended full-table deletions |
| `DROP DATABASE` | Prevents accidental database destruction |
| `DROP SCHEMA` | Prevents accidental schema destruction |
| `ALTER SYSTEM` | Prevents server-level config changes |

Detection normalizes whitespace and lowercases the statement before matching. Blocked statements are logged with `[BLOCKED]`, roll back the file's transaction, and mark it `FAILED`.

> Safety validation uses a character-by-character parser (not regex) to avoid `StackOverflowError` on large scripts.

---

## Error Handling & Skip Logic

Non-fatal errors are skipped at the statement level (savepoint rollback, `SKIPPED` status logged, execution continues):

| SQL State | Condition | Applies To |
|---|---|---|
| `23505` | Duplicate Key / Unique Violation | `INSERT` statements |
| `42710` | Constraint Already Exists | `ALTER TABLE ... ADD CONSTRAINT` |
| `42P07` | Table Already Exists | Any statement |
| `42701` | Column Already Exists | Any statement |

All other errors roll back the entire file transaction and mark it `FAILED`.

---

## Idempotency & Audit Trail

Each file is executed at most once, tracked via:

```sql
CREATE TABLE IF NOT EXISTS schema_migration_history (
    id SERIAL PRIMARY KEY,
    script_name TEXT UNIQUE,
    sql_content TEXT,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status TEXT
);
```

- `script_name` is the file's **relative path** from the source root (portable across machines)
- Records are upserted via `ON CONFLICT (script_name) DO UPDATE`, so a `FAILED` script can be re-run and updated to `SUCCESS`
- Statuses: `SUCCESS`, `FAILED`, `SKIPPED`

---

## Logging

- Every run produces `logs/execution_log_YYYYMMDD_HHmmss.txt`
- Log files are never overwritten
- Includes: connection details, per-statement status, error details (message, SQL State, error code), and final summary

---

## Multi-Database Support

Dialect-specific behavior is abstracted via `DbDialect`, with implementations for:
- PostgreSQL (primary/reference implementation)
- MySQL
- SQL Server
- Oracle

---

## Troubleshooting

| Problem | Cause & Resolution |
|---|---|
| `appsettings.json not found` | Must be in the same directory as the executable; check spelling (case-sensitive on Linux) |
| Schema does not exist | Verify schema name and that it's been created in the target DB |
| Schema mismatch error | Check user privileges and `search_path` configuration |
| SSL connection error | Use `require` for managed/cloud DBs, `disable` for local |
| `[BLOCKED]` unsafe UPDATE/DELETE | File contains a statement without a `WHERE` clause |
| File always skipped | Already recorded as `SUCCESS` in `schema_migration_history` |
| Log files not created | Executable needs write permission to create `logs/` |
| JVM crash on deploy server | Check available system memory (esp. on VMs running multiple services); tune heap size in `sqlexecutor.cfg` (jpackage app config) |

---

## License

Internal tool — add license terms as applicable.
