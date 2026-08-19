package deployment.sqlexecutor;

public class AppSettings {

    public Database database;
    public Folders folders;

    public static class Database {
        public String host;
        public int port;
        public String name;
        public String schema;
        public String user;
        public String password;
        public String sslmode;
    }

    public static class Folders {
        public String sourceDir;
        public String targetDir;
    }
}
