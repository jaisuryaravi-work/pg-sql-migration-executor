package deployment.sqlexecutor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExecutionLogger {

    private static BufferedWriter writer;
    private static String logFilePath;

    static {
        try {
            // 🔹 Create logs folder inside project root
            String projectRoot = Paths.get("").toAbsolutePath().toString();
            File logDir = new File(projectRoot, "logs");

            if (!logDir.exists()) {
                logDir.mkdir();
            }

            // 🔹 Create timestamp log file
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            logFilePath = logDir.getAbsolutePath() +
                    File.separator + "execution_log_" + timestamp + ".txt";

            writer = new BufferedWriter(new FileWriter(logFilePath, true));

            log("====================================================");
            log("DB SCRIPT EXECUTION LOG STARTED");
            log("Time : " + LocalDateTime.now());
            log("Log File : " + logFilePath);
            log("====================================================");

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize logging system", e);
        }
    }

    public static void log(String message) {
        try {

            // Print original message to console (with colors)
            System.out.println(message);

            // Remove ANSI escape codes before writing to file
            String cleanMessage = message.replaceAll("\\u001B\\[[;\\d]*m", "");

            writer.write(cleanMessage);
            writer.newLine();
            writer.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void close() {
        try {
            log("====================================================");
            log("EXECUTION FINISHED");
            log("Time : " + LocalDateTime.now());
            log("====================================================");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void logException(Throwable e) {
        log(ConsoleColor.RED + "Exception Type : " + e.getClass().getName() + ConsoleColor.RESET);
        log(ConsoleColor.RED + "Message        : " + e.getMessage() + ConsoleColor.RESET);

        for (StackTraceElement element : e.getStackTrace()) {
            log("    at " + element.toString());
        }

        if (e.getCause() != null) {
            log(ConsoleColor.YELLOW + "Caused By:" + ConsoleColor.RESET);
            logException(e.getCause());
        }
    }

}
