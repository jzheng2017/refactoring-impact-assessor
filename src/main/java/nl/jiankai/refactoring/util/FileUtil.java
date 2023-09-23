package nl.jiankai.refactoring.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.Objects;

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

    public static Collection<File> findFileRecursive(File directory, String fileName, String suffix) {
        return FileUtils
                .listFiles(directory, FileFilterUtils.suffixFileFilter(suffix), TrueFileFilter.INSTANCE)
                .stream()
                .filter(file -> Objects.equals(file.getName(), fileName))
                .toList();
    }

    public static class FileNotFoundException extends RuntimeException {
        public FileNotFoundException(String errorMessage) {
            super(errorMessage);
        }
    }
}
