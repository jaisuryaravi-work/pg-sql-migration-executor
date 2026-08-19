package deployment.sqlexecutor;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.sql.DatabaseMetaData;
import java.sql.Savepoint;

public class DbScriptExecutor {

	// =====================================================
	// DB CONNECTION LOG
	// =====================================================

	private static void logDbConnectionDetails(Connection connection) throws SQLException {

		DatabaseMetaData metaData = connection.getMetaData();

		ExecutionLogger
				.log(ConsoleColor.BLUE + "\n====================================================" + ConsoleColor.RESET);
		ExecutionLogger.log(ConsoleColor.GREEN + "Database Connection Established" + ConsoleColor.RESET);
		ExecutionLogger
				.log(ConsoleColor.BLUE + "====================================================" + ConsoleColor.RESET);

		// Use getters (since you moved to JSON config)
		ExecutionLogger.log(ConsoleColor.CYAN + "DB URL       : " + ConsoleColor.RESET + DbConfig.getDbUrl());

		ExecutionLogger.log(ConsoleColor.CYAN + "DB User      : " + ConsoleColor.RESET + DbConfig.getDbUser());

		ExecutionLogger
				.log(ConsoleColor.CYAN + "DB Product   : " + ConsoleColor.RESET + metaData.getDatabaseProductName());

		ExecutionLogger
				.log(ConsoleColor.CYAN + "DB Version   : " + ConsoleColor.RESET + metaData.getDatabaseProductVersion());

		ExecutionLogger.log(ConsoleColor.CYAN + "JDBC Driver  : " + ConsoleColor.RESET + metaData.getDriverName());

		ExecutionLogger.log(ConsoleColor.CYAN + "Driver Ver   : " + ConsoleColor.RESET + metaData.getDriverVersion());

		ExecutionLogger.log(ConsoleColor.CYAN + "Auto Commit  : " + ConsoleColor.RESET + connection.getAutoCommit());

		// Log Current Database Name
		ExecutionLogger.log(ConsoleColor.CYAN + "Database Name: " + ConsoleColor.RESET + connection.getCatalog());

		// Log Active Schema (REAL schema used by session)
		try (Statement stmt = connection.createStatement();
				java.sql.ResultSet rs = stmt.executeQuery("SHOW search_path")) {

			if (rs.next()) {
				ExecutionLogger.log(ConsoleColor.CYAN + "Active Schema: " + ConsoleColor.RESET + rs.getString(1));
			}
		}

		ExecutionLogger
				.log(ConsoleColor.BLUE + "====================================================" + ConsoleColor.RESET);

		// Source & Target Path
		ExecutionLogger.log(ConsoleColor.BLUE + "Source Directory  : " + ConsoleColor.RESET
				+ Paths.get(DbConfig.getSourceDir()).toAbsolutePath());

		String targetDir = DbConfig.getTargetDir();

		if (targetDir != null) {
			ExecutionLogger.log(ConsoleColor.BLUE + "Target Directory  : " + ConsoleColor.RESET
					+ Paths.get(targetDir).toAbsolutePath());
		}

		ExecutionLogger
				.log(ConsoleColor.BLUE + "====================================================\n" + ConsoleColor.RESET);
	}

	// =====================================================
	// MAIN
	// =====================================================

	/*
	 * public static void main(String[] args) {
	 * 
	 * // Apply pipeline argument overrides DbConfig.overrideFromArgs(args);
	 * 
	 * ExecutionLogger.log("===== DB Script Execution Started =====");
	 * 
	 * Path sourceRoot = Paths.get(DbConfig.getSourceDir());
	 * 
	 * try (Connection connection = DriverManager.getConnection(DbConfig.getDbUrl(),
	 * DbConfig.getDbUser(), DbConfig.getDbPassword())) {
	 * 
	 * connection.setAutoCommit(false); validateSchema(connection); try (Statement
	 * stmt = connection.createStatement()) { stmt.execute("SET search_path TO " +
	 * DbConfig.getSchema()); } ensureMigrationTable(connection);
	 * 
	 * // Validate active schema try (Statement stmt = connection.createStatement();
	 * ResultSet rs = stmt.executeQuery("SELECT current_schema()")) {
	 * 
	 * rs.next(); String activeSchema = rs.getString(1);
	 * 
	 * if (!activeSchema.equalsIgnoreCase(DbConfig.getSchema())) { throw new
	 * RuntimeException( "Schema mismatch! Expected: " + DbConfig.getSchema() +
	 * ", Active: " + activeSchema); } }
	 * 
	 * // Log DB details logDbConnectionDetails(connection);
	 * 
	 * // Ask for confirmation boolean proceed;
	 * 
	 * if (DbConfig.isPipelineMode()) {
	 * 
	 * // Pipeline mode should auto execute proceed = true;
	 * 
	 * } else {
	 * 
	 * Scanner scanner = new Scanner(System.in);
	 * 
	 * ExecutionLogger.log(ConsoleColor.YELLOW +
	 * "\nDo you want to proceed with script execution? (YES/NO): " +
	 * ConsoleColor.RESET);
	 * 
	 * String userInput = scanner.nextLine().trim();
	 * 
	 * proceed = userInput.equalsIgnoreCase("YES");
	 * 
	 * if (!proceed) { ExecutionLogger.log(ConsoleColor.RED +
	 * "\nExecution cancelled by user." + ConsoleColor.RESET);
	 * 
	 * return; // VERY IMPORTANT } }
	 * 
	 * // Collect all SQL files recursively List<Path> files =
	 * Files.walk(sourceRoot) .filter(p -> Files.isRegularFile(p) &&
	 * p.toString().toLowerCase().endsWith(".sql")).sorted()
	 * .collect(Collectors.toList());
	 * 
	 * 
	 * // Execute and move each file for (Path file : files) {
	 * executeSqlFile(connection, file); // validation happens here if
	 * (!DbConfig.isPipelineMode()) { FileMover.moveFile(file); } }
	 * ExecutionLogger.log("===== All Scripts Executed Successfully ====="); } catch
	 * (Exception e) { ExecutionLogger.log(ConsoleColor.RED +
	 * "[ERROR] Execution stopped due to error" + ConsoleColor.RESET);
	 * ExecutionLogger.logException(e); return;
	 * 
	 * } finally {
	 * 
	 * if (!DbConfig.isPipelineMode()) { waitForUser(); } } }
	 */

	// =====================================================
	// MAIN
	// =====================================================
	public static void main(String[] args) {

		DbConfig.overrideFromArgs(args);

		ExecutionLogger.log("===== DB Script Execution Started =====");

		Path sourceRoot = Paths.get(DbConfig.getSourceDir());

		// Summary list — collects result of every file
		List<FileSummary> summaryList = new ArrayList<>();

		try (Connection connection = DriverManager.getConnection(DbConfig.getDbUrl(), DbConfig.getDbUser(),
				DbConfig.getDbPassword())) {

			connection.setAutoCommit(false);
			validateSchema(connection);

			try (Statement stmt = connection.createStatement()) {
				stmt.execute("SET search_path TO " + DbConfig.getSchema());
			}

			ensureMigrationTable(connection);

			// Validate active schema
			try (Statement stmt = connection.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT current_schema()")) {
				rs.next();
				String activeSchema = rs.getString(1);
				if (!activeSchema.equalsIgnoreCase(DbConfig.getSchema())) {
					throw new RuntimeException(
							"Schema mismatch! Expected: " + DbConfig.getSchema() + ", Active: " + activeSchema);
				}
			}

			logDbConnectionDetails(connection);

			// Collect all SQL files recursively
			List<Path> files = Files.walk(sourceRoot)
					.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".sql")).sorted()
					.collect(Collectors.toList());

			int totalFiles = files.size();

			ExecutionLogger.log(ConsoleColor.CYAN + "\nTotal SQL files found : " + totalFiles + ConsoleColor.RESET);

			// Confirm before proceeding
			boolean proceed;
			if (DbConfig.isPipelineMode()) {
				proceed = true;
			} else {
				Scanner scanner = new Scanner(System.in);
				ExecutionLogger.log(ConsoleColor.YELLOW + "\nDo you want to proceed with script execution? (YES/NO): "
						+ ConsoleColor.RESET);
				String userInput = scanner.nextLine().trim();
				proceed = userInput.equalsIgnoreCase("YES");
				if (!proceed) {
					ExecutionLogger.log(ConsoleColor.RED + "\nExecution cancelled by user." + ConsoleColor.RESET);
					return;
				}
			}

			// -----------------------------------------------
			// FILE LOOP
			// -----------------------------------------------
			for (Path file : files) {

				long fileStart = System.currentTimeMillis();
				boolean fileSuccess = true;
				boolean fileSkipped = false;
				String errorMsg = null;
				int stmtCount = 0;
				int skippedStmtCount = 0;

				try {
					StmtExecResult result = executeSqlFile(connection, file);

					stmtCount = result.totalStatements;
					skippedStmtCount = result.skippedStatements;

					// -1 means SKIPPED (already executed or empty file)
					if (stmtCount == -1) {
						fileSkipped = true;
						stmtCount = 0;
					}

					if (!DbConfig.isPipelineMode()) {
						FileMover.moveFile(file);
					}

				} catch (Exception ex) {
					fileSuccess = false;
					errorMsg = ex.getMessage();
				}

				long fileTakenMs = System.currentTimeMillis() - fileStart;

				String relativePath = sourceRoot.relativize(file).toString();

				// Determine status
				String status;
				if (!fileSuccess) {
					status = "FAILED";
				} else if (fileSkipped) {
					status = "SKIPPED";
				} else {
					status = "SUCCESS";
				}

				summaryList.add(new FileSummary(relativePath, status, fileTakenMs, stmtCount, skippedStmtCount,
						fileSuccess, errorMsg));

				if (!fileSuccess) {
					ExecutionLogger.log(ConsoleColor.RED + "[ERROR] Stopping execution - failure in file : "
							+ relativePath + ConsoleColor.RESET);
					break;
				}
			}

			ExecutionLogger.log("===== All Files Processed =====");

		} catch (Exception e) {
			ExecutionLogger.log(ConsoleColor.RED + "[ERROR] Execution stopped due to error" + ConsoleColor.RESET);
			ExecutionLogger.logException(e);

		} finally {

			// Always print summary — even on error
			printExecutionSummary(summaryList);

			// ← ADD THIS — prints AFTER summary, always
			boolean allSuccess = summaryList.stream().noneMatch(s -> s.status.equals("FAILED"));

			if (allSuccess && !summaryList.isEmpty()) {
				System.out.println(
						ConsoleColor.GREEN + "===== All Scripts Executed Successfully =====" + ConsoleColor.RESET);
			} else if (!summaryList.isEmpty()) {
				System.out
						.println(ConsoleColor.RED + "===== Execution Completed With Errors =====" + ConsoleColor.RESET);
			}

			if (!DbConfig.isPipelineMode()) {
				waitForUser();
			}
		}
	}

	private static void ensureMigrationTable(Connection connection) throws SQLException {

		String sql = "CREATE TABLE IF NOT EXISTS schema_migration_history (" + "id SERIAL PRIMARY KEY, "
				+ "script_name TEXT UNIQUE, " + "sql_content TEXT, "
				+ "executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " + "status TEXT" + ")";

		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		}
	}

	private static boolean isScriptExecuted(Connection connection, String scriptName) throws SQLException {

		String sql = "SELECT 1 FROM schema_migration_history WHERE script_name = ? AND status = 'SUCCESS'";

		try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {

			ps.setString(1, scriptName);

			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static void recordScriptExecution(Connection connection, String scriptName, String status,
			String sqlContent) throws SQLException {

		ExecutionLogger.log(
				ConsoleColor.GREEN + "[DEBUG] Logging migration: " + scriptName + " -> " + status + ConsoleColor.RESET);

		String sql = "INSERT INTO schema_migration_history (script_name, status, sql_content) " + "VALUES (?, ?, ?) "
				+ "ON CONFLICT (script_name) " + "DO UPDATE SET executed_at = CURRENT_TIMESTAMP, "
				+ "status = EXCLUDED.status, " + "sql_content = EXCLUDED.sql_content";

		try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {

			ps.setString(1, scriptName);
			ps.setString(2, status);
			ps.setString(3, sqlContent);
			ps.executeUpdate();
		}
	}

	private static void validateSchema(Connection connection) throws SQLException {

		String schema = DbConfig.getSchema();

		String sql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			ps.setString(1, schema);

			try (ResultSet rs = ps.executeQuery()) {

				if (!rs.next()) {
					throw new RuntimeException("Schema does NOT exist in database: " + schema);
				}
			}
		}
	}

	/*
	 * // ===================================================== // SQL SAFETY
	 * VALIDATION -- OLD // =====================================================
	 * private static void validateSqlSafety(String sql) throws SQLException {
	 * 
	 * 
	 * String normalized = sql.trim().toLowerCase().replaceAll("\\s+", " ");
	 * 
	 * if (normalized.startsWith("update") && !normalized.contains(" where ")) {
	 * 
	 * ExecutionLogger.log(ConsoleColor.RED +
	 * "[BLOCKED] Unsafe UPDATE detected (missing WHERE)" + ConsoleColor.RESET);
	 * 
	 * ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql +
	 * ConsoleColor.RESET);
	 * 
	 * throw new SQLException("Unsafe UPDATE without WHERE"); }
	 * 
	 * if (normalized.startsWith("delete") && !normalized.contains(" where ")) {
	 * 
	 * ExecutionLogger.log(ConsoleColor.RED +
	 * "[BLOCKED] Unsafe DELETE detected (missing WHERE)" + ConsoleColor.RESET);
	 * 
	 * ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql +
	 * ConsoleColor.RESET);
	 * 
	 * throw new SQLException("Unsafe DELETE without WHERE"); }
	 * 
	 * if (normalized.contains("drop database")) {
	 * 
	 * ExecutionLogger.log(ConsoleColor.RED + "[BLOCKED] DROP DATABASE not allowed"
	 * + ConsoleColor.RESET);
	 * 
	 * ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql +
	 * ConsoleColor.RESET);
	 * 
	 * throw new SQLException("DROP DATABASE not allowed"); }
	 * 
	 * if (normalized.contains("drop schema")) {
	 * 
	 * ExecutionLogger.log(ConsoleColor.RED + "[BLOCKED] DROP SCHEMA not allowed" +
	 * ConsoleColor.RESET);
	 * 
	 * ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql +
	 * ConsoleColor.RESET);
	 * 
	 * throw new SQLException("DROP SCHEMA not allowed"); }
	 * 
	 * if (normalized.contains("alter system")) {
	 * 
	 * ExecutionLogger.log(ConsoleColor.RED + "[BLOCKED] ALTER SYSTEM not allowed" +
	 * ConsoleColor.RESET);
	 * 
	 * ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql +
	 * ConsoleColor.RESET);
	 * 
	 * throw new SQLException("ALTER SYSTEM not allowed"); } }
	 * 
	 */

	// =====================================================
	// SQL SAFETY VALIDATION -- NEW
	// =====================================================

	private static void validateSqlSafety(String sql) throws SQLException {

		String stripped = stripCommentsAndStrings(sql);
		String normalized = stripped.trim().toLowerCase().replaceAll("\\s+", " ");

		// -------------------------------------------------------
		// UPDATE without WHERE
		// -------------------------------------------------------
		if (normalized.startsWith("update") && !normalized.contains(" where ")) {

			ExecutionLogger
					.log(ConsoleColor.RED + "[BLOCKED] Unsafe UPDATE detected (missing WHERE)" + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql + ConsoleColor.RESET);

			throw new SQLException("Unsafe UPDATE without WHERE");
		}

		// -------------------------------------------------------
		// DELETE without WHERE
		// -------------------------------------------------------
		if (normalized.startsWith("delete") && !normalized.contains(" where ")) {

			ExecutionLogger
					.log(ConsoleColor.RED + "[BLOCKED] Unsafe DELETE detected (missing WHERE)" + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql + ConsoleColor.RESET);

			throw new SQLException("Unsafe DELETE without WHERE");
		}

		// -------------------------------------------------------
		// DROP DATABASE
		// -------------------------------------------------------
		if (normalized.contains("drop database")) {

			ExecutionLogger.log(ConsoleColor.RED + "[BLOCKED] DROP DATABASE not allowed" + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql + ConsoleColor.RESET);

			throw new SQLException("DROP DATABASE not allowed");
		}

		// -------------------------------------------------------
		// DROP SCHEMA
		// -------------------------------------------------------
		if (normalized.contains("drop schema")) {

			ExecutionLogger.log(ConsoleColor.RED + "[BLOCKED] DROP SCHEMA not allowed" + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql + ConsoleColor.RESET);

			throw new SQLException("DROP SCHEMA not allowed");
		}

		// -------------------------------------------------------
		// ALTER SYSTEM
		// -------------------------------------------------------
		if (normalized.contains("alter system")) {

			ExecutionLogger.log(ConsoleColor.RED + "[BLOCKED] ALTER SYSTEM not allowed" + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "SQL : " + sql + ConsoleColor.RESET);

			throw new SQLException("ALTER SYSTEM not allowed");
		}
	}

	// =====================================================
	// STRIP COMMENTS AND STRING LITERALS (NO REGEX, NO STACKOVERFLOW)
	// Handles: -- line comments, /* block comments */,
	// 'single-quoted strings' (with '' escapes),
	// $$ and $tag$ dollar-quoted blocks (functions/procedures)
	// =====================================================

	private static String stripCommentsAndStrings(String sql) {

		StringBuilder result = new StringBuilder(sql.length());

		int i = 0;
		int len = sql.length();

		while (i < len) {

			char c = sql.charAt(i);
			char next = (i + 1 < len) ? sql.charAt(i + 1) : '\0';

			// --------------------------------------------------
			// LINE COMMENT: -- ... \n
			// --------------------------------------------------
			if (c == '-' && next == '-') {
				// Skip everything until end of line
				while (i < len && sql.charAt(i) != '\n') {
					i++;
				}
				result.append(' ');
				continue;
			}

			// --------------------------------------------------
			// BLOCK COMMENT: /* ... */
			// --------------------------------------------------
			if (c == '/' && next == '*') {
				i += 2; // skip /*
				while (i < len) {
					if (sql.charAt(i) == '*' && (i + 1 < len) && sql.charAt(i + 1) == '/') {
						i += 2; // skip */
						break;
					}
					i++;
				}
				result.append(' ');
				continue;
			}

			// --------------------------------------------------
			// DOLLAR QUOTE: $$ ... $$ or $tag$ ... $tag$
			// --------------------------------------------------
			if (c == '$') {

				// Find the closing $ of the opening tag
				int tagEnd = sql.indexOf('$', i + 1);

				if (tagEnd > i) {

					String dollarTag = sql.substring(i, tagEnd + 1); // e.g. $$ or $func$

					// Find the matching closing dollar tag
					int closePos = sql.indexOf(dollarTag, tagEnd + 1);

					if (closePos >= 0) {
						// Skip entire dollar-quoted block, replace with a space placeholder
						i = closePos + dollarTag.length();
						result.append(' ');
						continue;
					}
				}
			}

			// --------------------------------------------------
			// SINGLE-QUOTED STRING: '...' ('' = escaped quote)
			// --------------------------------------------------
			if (c == '\'') {

				i++; // skip opening quote

				while (i < len) {

					char sc = sql.charAt(i);

					// Escaped quote '' — skip both chars and stay inside string
					if (sc == '\'' && (i + 1 < len) && sql.charAt(i + 1) == '\'') {
						i += 2;
						continue;
					}

					// Closing quote
					if (sc == '\'') {
						i++; // skip closing quote
						break;
					}

					i++;
				}

				result.append("''"); // placeholder — keeps SQL structure intact
				continue;
			}

			// --------------------------------------------------
			// NORMAL CHARACTER — keep it
			// --------------------------------------------------
			result.append(c);
			i++;
		}

		return result.toString();
	}

	private static void waitForUser() {
		try {
			System.out.println("========================================");
			System.out.println("Program finished. Press ENTER to exit...");
			System.out.println("========================================");
			new java.util.Scanner(System.in).nextLine();
		} catch (Exception ignored) {
		}
	}

	
	/*
	// =====================================================
	// SQL TYPE DETECTION (ADDED, NO REMOVALS)
	// =====================================================
	private static boolean isAlterAddConstraint(String sql) {
		String s = sql.toUpperCase().replaceAll("\\s+", " ");
		return s.contains("ALTER TABLE") && s.contains("ADD CONSTRAINT");
	}

	private static boolean isInsertStatement(String sql) {
		String s = sql.trim().toUpperCase().replaceAll("\\s+", " ");
		return s.startsWith("INSERT INTO");
	}*/
	
	
	
	// =====================================================
	// STRIP LEADING COMMENTS (for statement-type detection)
	// =====================================================
	private static String stripLeadingComments(String sql) {
	    String s = sql;
	    boolean changed = true;
	    while (changed) {
	        changed = false;
	        s = s.trim();
	        if (s.startsWith("--")) {
	            int nl = s.indexOf('\n');
	            s = (nl == -1) ? "" : s.substring(nl + 1);
	            changed = true;
	        } else if (s.startsWith("/*")) {
	            int end = s.indexOf("*/");
	            s = (end == -1) ? "" : s.substring(end + 2);
	            changed = true;
	        }
	    }
	    return s;
	}

	// =====================================================
	// SQL TYPE DETECTION (ADDED, NO REMOVALS)
	// =====================================================
	private static boolean isAlterAddConstraint(String sql) {
	    String s = stripLeadingComments(sql).toUpperCase().replaceAll("\\s+", " ");
	    return s.contains("ALTER TABLE") && s.contains("ADD CONSTRAINT");
	}

	private static boolean isInsertStatement(String sql) {
	    String s = stripLeadingComments(sql).toUpperCase().replaceAll("\\s+", " ");
	    return s.startsWith("INSERT INTO");
	}
	
	

	private static boolean isAlreadyExistsError(SQLException ex) {

		if (ex == null)
			return false;

		String state = ex.getSQLState();

		if (state == null)
			return false;

		switch (state) {

		case "42P07": // table exists
		case "42701": // column exists
			// case "42710": // constraint exists
			// case "42723": // function exists
			// case "42P06": // schema exists
			// case "42P16": // multiple primary keys
			return true;

		default:
			return false;
		}
	}

	private static String classifySkipReason(SQLException ex) {

		if (ex == null)
			return "UNKNOWN";

		String state = ex.getSQLState();

		if ("23505".equals(state))
			return "DUPLICATE KEY";

		if ("42710".equals(state))
			return "CONSTRAINT ALREADY EXISTS";

		if ("42P07".equals(state))
			return "TABLE ALREADY EXISTS";

		if ("42701".equals(state))
			return "COLUMN ALREADY EXISTS";

		if ("42723".equals(state))
			return "FUNCTION ALREADY EXISTS";

		if ("42P06".equals(state))
			return "SCHEMA ALREADY EXISTS";

		return "SQLSTATE " + state;
	}

	// =====================================================
	// ⭐ CORE FIX – SMART SKIP LOGIC
	// =====================================================
	private static boolean canSkip(SQLException ex, String sql) {

		String state = ex.getSQLState();

		if (state == null)
			return false;

		// INSERT duplicate
		if (isInsertStatement(sql) && "23505".equals(state)) {
			return true;
		}

		// ALTER ADD CONSTRAINT exists
		if (isAlterAddConstraint(sql) && "42710".equals(state)) {
			return true;
		}

		// Generic already exists
		if (isAlreadyExistsError(ex)) {
			return true;
		}

		return false;
	}

	private static void validateSqlFile(Path file) {
		if (!file.getFileName().toString().toLowerCase().endsWith(".sql")) {

			ExecutionLogger
					.log(ConsoleColor.RED + "\n[ERROR] INVALID FILE FOUND – EXECUTION STOPPED" + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "File : " + file.toAbsolutePath() + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "Reason : Only .sql files are allowed in source directory"
					+ ConsoleColor.RESET);

			throw new IllegalStateException("Non-SQL file detected: " + file.toAbsolutePath());
		}
	}

	private static List<String> splitSqlStatements(String sql) {

		List<String> statements = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		boolean inLineComment = false;
		boolean inBlockComment = false;
		boolean inDollarQuote = false;

		String dollarTag = null;

		for (int i = 0; i < sql.length(); i++) {

			char c = sql.charAt(i);
			char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

			// -------------------------------
			// LINE COMMENT --
			// -------------------------------
			if (!inSingleQuote && !inDoubleQuote && !inDollarQuote) {
				if (!inLineComment && c == '-' && next == '-') {
					inLineComment = true;
				}
			}

			if (inLineComment && c == '\n') {
				inLineComment = false;
			}

			// -------------------------------
			// BLOCK COMMENT /* */
			// -------------------------------
			if (!inSingleQuote && !inDoubleQuote && !inDollarQuote) {
				if (!inBlockComment && c == '/' && next == '*') {
					inBlockComment = true;
				} else if (inBlockComment && c == '*' && next == '/') {
					inBlockComment = false;
					current.append("*/");
					i++;
					continue;
				}
			}

			if (inLineComment || inBlockComment) {
				current.append(c);
				continue;
			}

			// -------------------------------
			// DOLLAR QUOTE DETECTION
			// Handles $$ or $function$
			// -------------------------------
			if (!inSingleQuote && !inDoubleQuote && c == '$') {

				int end = sql.indexOf('$', i + 1);

				if (end > i) {

					String tag = sql.substring(i, end + 1); // $tag$

					if (!inDollarQuote) {
						inDollarQuote = true;
						dollarTag = tag;
						current.append(tag);
						i = end;
						continue;
					}

					if (inDollarQuote && tag.equals(dollarTag)) {
						inDollarQuote = false;
						current.append(tag);
						i = end;
						continue;
					}
				}
			}

			// -------------------------------
			// SINGLE QUOTE
			// -------------------------------
			if (c == '\'' && !inDoubleQuote && !inDollarQuote) {

				if (inSingleQuote && next == '\'') {
					current.append("''");
					i++;
					continue;
				}

				inSingleQuote = !inSingleQuote;
			}

			// -------------------------------
			// DOUBLE QUOTE
			// -------------------------------
			if (c == '"' && !inSingleQuote && !inDollarQuote) {
				inDoubleQuote = !inDoubleQuote;
			}

			// -------------------------------
			// STATEMENT END ;
			// -------------------------------
			if (c == ';' && !inSingleQuote && !inDoubleQuote && !inDollarQuote) {

				String stmt = current.toString().trim();

				if (!stmt.isEmpty()) {
					statements.add(stmt);
				}

				current.setLength(0);

			} else {
				current.append(c);
			}
		}

		if (current.length() > 0) {
			String stmt = current.toString().trim();
			if (!stmt.isEmpty()) {
				statements.add(stmt);
			}
		}

		return statements;
	}

	private static boolean isTransactionControlStatement(String sql) {

		String s = sql.trim().toUpperCase();

		return s.equals("COMMIT") || s.equals("COMMIT;") || s.equals("ROLLBACK") || s.equals("ROLLBACK;")
				|| s.equals("BEGIN") || s.equals("BEGIN;") || s.equals("START TRANSACTION")
				|| s.equals("START TRANSACTION;");
	}

	private static StmtExecResult executeSqlFile(Connection connection, Path sqlFile) throws Exception {

		Path sourceRoot = Paths.get(DbConfig.getSourceDir());
		String scriptName = sourceRoot.relativize(sqlFile).toString();

		if (isScriptExecuted(connection, scriptName)) {

			ExecutionLogger
					.log(ConsoleColor.YELLOW + "[SKIPPED] Script already executed: " + scriptName + ConsoleColor.RESET);

			return new StmtExecResult(-1, 0);
		}

		ExecutionLogger
				.log(ConsoleColor.BLUE + "\n====================================================" + ConsoleColor.RESET);
		ExecutionLogger
				.log(ConsoleColor.BLUE + "Executing SQL File : " + sqlFile.toAbsolutePath() + ConsoleColor.RESET);
		ExecutionLogger
				.log(ConsoleColor.BLUE + "====================================================" + ConsoleColor.RESET);

		// -------------------------------
		// READ FILE
		// -------------------------------
		StringBuilder sqlBuilder = new StringBuilder();

		try (BufferedReader reader = Files.newBufferedReader(sqlFile)) {
			String line;
			while ((line = reader.readLine()) != null) {
				sqlBuilder.append(line).append(System.lineSeparator());
			}
		}

		String sqlContent = sqlBuilder.toString().trim();

		if (sqlContent.isEmpty()) {
			ExecutionLogger.log(ConsoleColor.YELLOW + "[INFO] Empty SQL file – skipping" + ConsoleColor.RESET);

			return new StmtExecResult(-1, 0);
		}

		List<String> statements = splitSqlStatements(sqlContent);

		int count = 1;
		int skippedStatements = 0;

		try (Statement statement = connection.createStatement()) {

			for (String stmt : statements) {

				stmt = stmt.trim();
				if (stmt.isEmpty())
					continue;

				if (isTransactionControlStatement(stmt)) {

					ExecutionLogger.log(ConsoleColor.YELLOW + "[STATEMENT " + count
							+ " STATUS] SKIPPED (TRANSACTION CONTROL)" + ConsoleColor.RESET);

					count++;
					continue;
				}

				ExecutionLogger.log(ConsoleColor.CYAN + "\n[STATEMENT " + count + " START]" + ConsoleColor.RESET);

				ExecutionLogger.log(stmt + ";");

				// Safety validation
				validateSqlSafety(stmt);
				Savepoint sp = connection.setSavepoint();

				/* This Is OLD code before adding time taken and rows effected */
				/*
				 * try {
				 * 
				 * statement.execute(stmt);
				 * 
				 * connection.releaseSavepoint(sp);
				 * 
				 * ExecutionLogger.log(ConsoleColor.GREEN + "[STATEMENT " + count +
				 * " STATUS] SUCCESS" + ConsoleColor.RESET);
				 * 
				 * }
				 */

				/* NEW CODE STARTS */

				try {

					long startTime = System.currentTimeMillis();
					statement.execute(stmt);
					long elapsedMs = System.currentTimeMillis() - startTime;

					connection.releaseSavepoint(sp);

					int rowsAffected = statement.getUpdateCount();

					// --------------------------------------------------
					// FORMAT TIME
					// --------------------------------------------------
					String timeTaken;
					if (elapsedMs < 1000) {
						timeTaken = elapsedMs + " ms";
					} else if (elapsedMs < 60_000) {
						timeTaken = String.format("%.2f sec", elapsedMs / 1000.0);
					} else if (elapsedMs < 3_600_000) {
						long minutes = elapsedMs / 60_000;
						long seconds = (elapsedMs % 60_000) / 1000;
						timeTaken = minutes + " min " + seconds + " sec";
					} else {
						long hours = elapsedMs / 3_600_000;
						long minutes = (elapsedMs % 3_600_000) / 60_000;
						long seconds = (elapsedMs % 60_000) / 1000;
						timeTaken = hours + " hr " + minutes + " min " + seconds + " sec";
					}

					// --------------------------------------------------
					// FORMAT ROWS
					// --------------------------------------------------
					String rowsInfo;
					if (rowsAffected >= 0) {
						rowsInfo = rowsAffected + (rowsAffected == 1 ? " row" : " rows");
					} else {
						rowsInfo = "N/A (DDL — rows not applicable)";
					}

					ExecutionLogger
							.log(ConsoleColor.GREEN + "[STATEMENT " + count + " STATUS] SUCCESS" + ConsoleColor.RESET);

					ExecutionLogger.log(
							ConsoleColor.CYAN + "[STATEMENT " + count + " TIME  ] " + timeTaken + ConsoleColor.RESET);

					ExecutionLogger.log(
							ConsoleColor.CYAN + "[STATEMENT " + count + " ROWS  ] " + rowsInfo + ConsoleColor.RESET);

				}

				/* NEW CODE ENDS */

				catch (SQLException ex) {

					if (canSkip(ex, stmt) || isAlreadyExistsError(ex)) {

						connection.rollback(sp);
						skippedStatements++;

						ExecutionLogger.log(ConsoleColor.YELLOW + "[STATEMENT " + count + " STATUS] SKIPPED ("
								+ classifySkipReason(ex) + ")" + ConsoleColor.RESET);

						ExecutionLogger.log(ConsoleColor.YELLOW + "DETAIL : " + ex.getMessage() + ConsoleColor.RESET);

					} else {

						connection.rollback();

						recordScriptExecution(connection, scriptName, "FAILED", sqlContent);
						connection.commit();

						ExecutionLogger
								.log(ConsoleColor.RED + "[STATEMENT " + count + " STATUS] FAILED" + ConsoleColor.RESET);

						ExecutionLogger.log(ConsoleColor.YELLOW + "ERROR : " + ex.getMessage() + ConsoleColor.RESET);

						throw ex;
					}
				}

				count++;
			}

			// Commit after all statements succeed
			connection.commit();

			recordScriptExecution(connection, scriptName, "SUCCESS", sqlContent);
			connection.commit();

		} catch (SQLException ex) {

			connection.rollback();

			ExecutionLogger.log(
					ConsoleColor.RED + "\n[ERROR] ERROR IN FILE : " + sqlFile.toAbsolutePath() + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "PostgreSQL Message : " + ex.getMessage() + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "SQL State : " + ex.getSQLState() + ConsoleColor.RESET);

			ExecutionLogger.log(ConsoleColor.YELLOW + "Error Code : " + ex.getErrorCode() + ConsoleColor.RESET);

			throw ex;
		}
		return new StmtExecResult(count - 1, skippedStatements);
	}

	/* SUMMARY & PROGRESS BAR CHANGES 1 - START */

	// =====================================================
	// COUNT TOTAL SQL FILES (RECURSIVE)
	// =====================================================
	private static int countSqlFiles(Path rootDir) throws Exception {
		return (int) Files.walk(rootDir)
				.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".sql")).count();
	}

	// =====================================================
	// STATEMENT EXECUTION RESULT HOLDER (total vs skipped per file)
	// =====================================================
	private static class StmtExecResult {
		int totalStatements;
		int skippedStatements;

		StmtExecResult(int totalStatements, int skippedStatements) {
			this.totalStatements = totalStatements;
			this.skippedStatements = skippedStatements;
		}
	}

	// =====================================================
	// FILE EXECUTION SUMMARY HOLDER
	// =====================================================
	private static class FileSummary {
		String filePath;
		String status;
		long timeTakenMs;
		int totalStatements;
		int skippedStatements;
		boolean committed;
		String errorMessage;

		FileSummary(String filePath, String status, long timeTakenMs, int totalStatements, int skippedStatements,
				boolean committed, String errorMessage) {
			this.filePath = filePath;
			this.status = status;
			this.timeTakenMs = timeTakenMs;
			this.totalStatements = totalStatements;
			this.skippedStatements = skippedStatements;
			this.committed = committed;
			this.errorMessage = errorMessage;
		}
	}

	// =====================================================
	// EXECUTION SUMMARY (CONSOLE ONLY — NOT LOGGED)
	// =====================================================
	// =====================================================
	// EXECUTION SUMMARY (CONSOLE + LOG FILE)
	// =====================================================
	private static void printExecutionSummary(List<FileSummary> summaryList) {

		if (summaryList == null || summaryList.isEmpty())
			return;

		final int MAX_PATH_WIDTH = 55;

		int pathColWidth = summaryList.stream().mapToInt(s -> s.filePath.length()).max().orElse(20);

		pathColWidth = Math.max(Math.min(pathColWidth, MAX_PATH_WIDTH), 20);

		int srNoColWidth = 6;
		int statusColWidth = 25;
		int timeColWidth = 15;
		int stmtColWidth = 10;

		int totalWidth = srNoColWidth + pathColWidth + statusColWidth + timeColWidth + stmtColWidth + 14;
		String divider = "=".repeat(totalWidth);

		ExecutionLogger.log("");
		ExecutionLogger.log(ConsoleColor.BLUE + divider + ConsoleColor.RESET);
		ExecutionLogger.log(ConsoleColor.GREEN + " EXECUTION SUMMARY" + ConsoleColor.RESET);
		ExecutionLogger.log(ConsoleColor.BLUE + divider + ConsoleColor.RESET);

		String header = String.format("  %-" + srNoColWidth + "s %-" + pathColWidth + "s   %-" + statusColWidth
				+ "s   %-" + timeColWidth + "s   %-" + stmtColWidth + "s", "SR#", "FILE PATH", "STATUS", "TIME TAKEN",
				"STATEMENTS");
		ExecutionLogger.log(ConsoleColor.CYAN + header + ConsoleColor.RESET);

		ExecutionLogger.log(ConsoleColor.BLUE + divider + ConsoleColor.RESET);

		int srNo = 1;

		for (FileSummary s : summaryList) {

			String statusColor;
			if (s.status.equals("SUCCESS")) {
				statusColor = ConsoleColor.GREEN;
			} else if (s.status.equals("SKIPPED")) {
				statusColor = ConsoleColor.YELLOW;
			} else {
				statusColor = ConsoleColor.RED;
			}

			String stmtDisplay = s.skippedStatements > 0 ? s.totalStatements + " (" + s.skippedStatements + " skip)"
					: String.valueOf(s.totalStatements);

			List<String> pathLines = wrapPath(s.filePath, pathColWidth);
			String srLabel = srNo + ")";

			for (int i = 0; i < pathLines.size(); i++) {

				if (i == 0) {
					String row = String.format(
							"  %-" + srNoColWidth + "s %-" + pathColWidth + "s   " + statusColor + "%-" + statusColWidth
									+ "s" + ConsoleColor.RESET + "   %-" + timeColWidth + "s   %-" + stmtColWidth + "s",
							srLabel, pathLines.get(i), s.status, formatTime(s.timeTakenMs), stmtDisplay);
					ExecutionLogger.log(row);
				} else {
					String row = String.format("  %-" + srNoColWidth + "s %-" + pathColWidth + "s", "",
							pathLines.get(i));
					ExecutionLogger.log(row);
				}
			}

			if (s.errorMessage != null) {
				String[] errorLines = s.errorMessage.split("\n");
				ExecutionLogger.log(ConsoleColor.RED + "      └─ ERROR : " + errorLines[0] + ConsoleColor.RESET);
				for (int i = 1; i < errorLines.length; i++) {
					ExecutionLogger
							.log(ConsoleColor.RED + "                 " + errorLines[i].trim() + ConsoleColor.RESET);
				}
				if (!s.committed) { // <-- ADD THIS BLOCK
					ExecutionLogger.log(ConsoleColor.RED
							+ "      └─ NOTICE  : All statements in this file were ROLLED BACK - nothing committed to the database"
							+ ConsoleColor.RESET);
				}
			}

			srNo++;
		}

		ExecutionLogger.log(ConsoleColor.BLUE + divider + ConsoleColor.RESET);

		long totalMs = summaryList.stream().mapToLong(s -> s.timeTakenMs).sum();
		int totalStmts = summaryList.stream().mapToInt(s -> s.totalStatements).sum();
		int totalSkippedStmts = summaryList.stream().mapToInt(s -> s.skippedStatements).sum();
		long succeeded = summaryList.stream().filter(s -> s.status.equals("SUCCESS")).count();
		long failed = summaryList.stream().filter(s -> s.status.equals("FAILED")).count();
		long skipped = summaryList.stream().filter(s -> s.status.equals("SKIPPED")).count();

		String totalStmtDisplay = totalSkippedStmts > 0 ? totalStmts + " (" + totalSkippedStmts + " skip)"
				: String.valueOf(totalStmts);

		String totalsRow = String.format(
				"  %-" + srNoColWidth + "s %-" + pathColWidth + "s   %-" + statusColWidth + "s   %-" + timeColWidth
						+ "s   %-" + stmtColWidth + "s",
				"", "TOTAL (" + summaryList.size() + " files)",
				succeeded + " ok / " + failed + " fail / " + skipped + " skip", formatTime(totalMs), totalStmtDisplay);
		ExecutionLogger.log(ConsoleColor.CYAN + totalsRow + ConsoleColor.RESET);

		ExecutionLogger.log(ConsoleColor.BLUE + divider + ConsoleColor.RESET);
		ExecutionLogger.log("");
	}

	// =====================================================
	// WRAP LONG FILE PATH INTO MULTIPLE LINES (BREAK AT \)
	// =====================================================
	private static List<String> wrapPath(String path, int maxWidth) {

		List<String> lines = new ArrayList<>();

		if (path.length() <= maxWidth) {
			lines.add(path);
			return lines;
		}

		// Try to break at backslash/forward slash boundaries for readability
		String[] parts = path.split("(?<=[\\\\/])");
		StringBuilder current = new StringBuilder();

		for (String part : parts) {
			if (current.length() + part.length() > maxWidth) {
				if (current.length() > 0) {
					lines.add(current.toString());
					current.setLength(0);
				}
			}
			current.append(part);
		}

		if (current.length() > 0) {
			lines.add(current.toString());
		}

		return lines.isEmpty() ? List.of(path) : lines;
	}

	// =====================================================
	// FORMAT TIME — REUSABLE UTILITY
	// =====================================================
	private static String formatTime(long elapsedMs) {

		if (elapsedMs < 1000) {
			return elapsedMs + " ms";

		} else if (elapsedMs < 60_000) {
			return String.format("%.2f sec", elapsedMs / 1000.0);

		} else if (elapsedMs < 3_600_000) {
			long minutes = elapsedMs / 60_000;
			long seconds = (elapsedMs % 60_000) / 1000;
			return minutes + " min " + seconds + " sec";

		} else {
			long hours = elapsedMs / 3_600_000;
			long minutes = (elapsedMs % 3_600_000) / 60_000;
			long seconds = (elapsedMs % 60_000) / 1000;
			return hours + " hr " + minutes + " min " + seconds + " sec";
		}
	}

	/* SUMMARY & PROGRESS BAR CHANGES 1 - ENDS */

}
