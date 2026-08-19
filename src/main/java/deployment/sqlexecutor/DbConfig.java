package deployment.sqlexecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

public class DbConfig {

    private static AppSettings settings;

    // Pipeline overrides
    private static String argHost;
    private static String argPort;
    private static String argDb;
    private static String argUser;
    private static String argPassword;
    private static String argSchema;
    private static String argSqlFolder;
    private static String argTargetDir;
    private static String argSslMode;
    
    private static boolean pipelineMode = false;

    public static boolean isPipelineMode() {
        return pipelineMode;
    }

    // =====================================================
    // PIPELINE ARGUMENT OVERRIDE
    // =====================================================
    public static void overrideFromArgs(String[] args) {

        int offset = 0;

        if (args.length > 0 && args[0].equalsIgnoreCase("-p")) {
            pipelineMode = true;
            offset = 1;
            System.out.println("Pipeline configuration detected");
        }

        if (pipelineMode) {

            if (args.length < offset + 9) {
                throw new RuntimeException(
                    "\n[ERROR] Missing required pipeline parameters.\n" +
                    "Expected format:\n" +
                    "-p YES <SQL_FOLDER> <HOST> <PORT> <DB> <USER> <PASSWORD> <SCHEMA> <SSL_MODE>\n"
                );
            }

            argSqlFolder = args[offset + 1];
            argHost = args[offset + 2];
            argPort = args[offset + 3];
            argDb = args[offset + 4];
            argUser = args[offset + 5];
            argPassword = args[offset + 6];
            argSchema = args[offset + 7];
            argSslMode = args[offset + 8];

            // targetDir optional
            if (args.length > offset + 9) {
                argTargetDir = args[offset + 9];
            }
        }
    }

    // =====================================================
    // LOAD JSON ONLY IF NOT PIPELINE
    // =====================================================
    private static void loadJsonConfig() {

        if (pipelineMode) {
            return; // pipeline mode should not load json
        }

        if (settings != null) {
            return;
        }

        try {

            String workingDir = System.getProperty("user.dir");
            File file = new File(workingDir, "appsettings.json");

            System.out.println("Loading config from: " + file.getAbsolutePath());

            if (!file.exists()) {
                throw new RuntimeException(
                    "[ERROR] appsettings.json not found at: " + file.getAbsolutePath());
            }

            ObjectMapper mapper = new ObjectMapper();
            settings = mapper.readValue(file, AppSettings.class);

        } catch (Exception e) {
            throw new RuntimeException("[ERROR] Failed to load appsettings.json", e);
        }
    }

    // =====================================================
    // DATABASE URL
    // =====================================================
    public static String getDbUrl() {

        if (!pipelineMode) {
            loadJsonConfig();
        }

        String host = (pipelineMode && argHost != null) ? argHost : settings.database.host;
        String db = (pipelineMode && argDb != null) ? argDb : settings.database.name;
        String schema = (pipelineMode && argSchema != null) ? argSchema : settings.database.schema;
        String sslmode = (pipelineMode && argSslMode != null) ? argSslMode : settings.database.sslmode;
        int port = (pipelineMode && argPort != null) ? Integer.parseInt(argPort) : settings.database.port;

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db + "?currentSchema=" + schema;

        if (sslmode != null &&
                !sslmode.equalsIgnoreCase("disable") &&
                !sslmode.equalsIgnoreCase("disabled") &&
                !sslmode.isBlank()) {

            url = url + "&sslmode=" + sslmode;
        }

        return url;
    }

    public static String getDbUser() {

        if (!pipelineMode) {
            loadJsonConfig();
        }

        return (pipelineMode && argUser != null) ? argUser : settings.database.user;
    }

    public static String getDbPassword() {

        if (!pipelineMode) {
            loadJsonConfig();
        }

        return (pipelineMode && argPassword != null) ? argPassword : settings.database.password;
    }

    public static String getSchema() {

        if (!pipelineMode) {
            loadJsonConfig();
        }

        return (pipelineMode && argSchema != null) ? argSchema : settings.database.schema;
    }

    public static String getSourceDir() {

        if (!pipelineMode) {
            loadJsonConfig();
        }

        if (pipelineMode && argSqlFolder != null) {
            return argSqlFolder;
        }

        if (argSqlFolder != null) {
            return settings.folders.sourceDir + File.separator + argSqlFolder;
        }

        return settings.folders.sourceDir;
    }

    public static String getTargetDir() {

        if (pipelineMode) {

            if (argTargetDir != null) {
                return argTargetDir;
            }

            // pipeline mode does not require target directory
            return null;
        }

        loadJsonConfig();

        return settings.folders.targetDir;
    }
}
