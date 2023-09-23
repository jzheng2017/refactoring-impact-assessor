package nl.jiankai.refactoring.util;

import java.io.File;
import java.io.FilenameFilter;

public class FileUtil {
    private static final String POM_FILE = "pom.xml";

    public static File findPomFile(File projectRootPath) {
        return findFileNonRecursive(projectRootPath, (dir, fileName) -> POM_FILE.equals(fileName));
    }

    public static File findFileNonRecursive(File directory, FilenameFilter filter) {
        File[] foundFiles = directory.listFiles(filter);

        if (foundFiles != null && foundFiles.length > 0) {
            return foundFiles[0];
        }

        throw new FileNotFoundException("Could not find file '%s'".formatted(directory.getName()));
    }

    public static class FileNotFoundException extends RuntimeException {
        public FileNotFoundException(String errorMessage) {
            super(errorMessage);
        }
    }
}
