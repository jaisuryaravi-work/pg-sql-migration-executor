package deployment.sqlexecutor;

public class ConsoleColor {

    private static final boolean ENABLE_COLOR = detectColorSupport();

    public static final String RESET  = ENABLE_COLOR ? "\u001B[0m" : "";

    public static final String GREEN  = ENABLE_COLOR ? "\u001B[32m" : "";
    public static final String RED    = ENABLE_COLOR ? "\u001B[31m" : "";
    public static final String YELLOW = ENABLE_COLOR ? "\u001B[33m" : "";
    public static final String BLUE   = ENABLE_COLOR ? "\u001B[34m" : "";
    public static final String CYAN   = ENABLE_COLOR ? "\u001B[36m" : "";

    private static boolean detectColorSupport() {

        try {

            String os = System.getProperty("os.name").toLowerCase();

            // Linux & Mac terminals support ANSI
            if (os.contains("linux") || os.contains("mac")) {
                return true;
            }

            // Windows detection
            if (os.contains("win")) {

                // Windows Terminal
                if (System.getenv("WT_SESSION") != null) {
                    return true;
                }

                // VS Code terminal
                if (System.getenv("TERM_PROGRAM") != null) {
                    return true;
                }

                // Git Bash / modern terminals
                if (System.getenv("TERM") != null) {
                    return true;
                }

                return false;
            }

        } catch (Exception ignored) {}

        return false;
    }

    public static String green(String text) {
        return GREEN + text + RESET;
    }

    public static String red(String text) {
        return RED + text + RESET;
    }

    public static String yellow(String text) {
        return YELLOW + text + RESET;
    }

    public static String blue(String text) {
        return BLUE + text + RESET;
    }

    public static String cyan(String text) {
        return CYAN + text + RESET;
    }
}
