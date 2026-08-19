
package deployment.sqlexecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileMover {

    public static void moveFile(Path sourceFile) throws Exception {

        // Use getter methods, NOT static constants
        Path sourceRoot = Paths.get(DbConfig.getSourceDir());
        Path targetRoot = Paths.get(DbConfig.getTargetDir());

        // Compute relative path
        Path relativePath = sourceRoot.relativize(sourceFile);
        Path targetFile = targetRoot.resolve(relativePath);

        // Ensure target directory exists
        Files.createDirectories(targetFile.getParent());

        // Move file
        Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        ExecutionLogger.log("Moved file to: " + targetFile);
    }
}