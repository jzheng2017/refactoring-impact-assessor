package nl.jiankai.refactoring.core.storage.filestorage;

import nl.jiankai.refactoring.core.storage.api.StorageService;
import nl.jiankai.refactoring.core.storage.api.StorageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class LocalFileStorageService implements StorageService<String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileStorageService.class);
    public static final String SEPARATOR = "\n";
    private final String fileLocation;
    private final boolean createIfMissing;

    public LocalFileStorageService(String fileLocation, boolean createIfMissing) {
        this.fileLocation = fileLocation;
        this.createIfMissing = createIfMissing;
        createFileIfMissing();
    }

    @Override
    public Stream<String> read() {
        try {
            List<String> content = Files.readAllLines(Paths.get(fileLocation));
            return content.stream();
        } catch (IOException e) {
            LOGGER.warn("Could not read the contents of '{}'", fileLocation);
            return Stream.empty();
        }
    }

    @Override
    public void write(String content) {
        write(content, false);
    }

    @Override
    public void write(List<String> list) {
        writeList(list, false);
    }

    @Override
    public void append(String content) {
        write(content, true);
    }

    @Override
    public void append(List<String> list) {
        writeList(list, true);
    }

    @Override
    public void clear() {
        write("", false);
    }

    private void writeList(List<String> list, boolean append) {
        write(String.join(SEPARATOR, list), append);
    }

    private void write(String content, boolean append) {
        createFileIfMissing();

        if (append && !fileIsEmpty()) {
            content = SEPARATOR + content;
        }

        try (FileOutputStream outputStream = new FileOutputStream(fileLocation, append)) {
            outputStream.write(content.getBytes());
            LOGGER.info("Successfully written to '{}'", fileLocation);
        } catch (IOException e) {
            LOGGER.warn("Could not write the content to the file '{}'. Reason: {}", fileLocation, e.getMessage());
        }
    }

    private void createFileIfMissing() {
        File file = new File(fileLocation);
        if (createIfMissing && !file.exists()) {
            if (file.getParentFile().exists()) {
                tryCreateFile(file);
            } else if (file.getParentFile().mkdirs()) {
                tryCreateFile(file);
            } else {
                LOGGER.warn("Could not successfully create the directory for the file at location {}", file);
            }
        }
    }

    private void tryCreateFile(File file) {
        try {
            if (file.createNewFile()) {
                LOGGER.info("File created at location '{}'", fileLocation);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not create file at location '{}'", fileLocation);
        }
    }

    private boolean fileIsEmpty() {
        return new File(fileLocation).length() == 0;
    }

    @Override
    public void addListener(StorageListener<String> listener) {
        throw new UnsupportedOperationException("Can not listen to this class");
    }

    @Override
    public void removeListener(StorageListener<String> listener) {
        throw new UnsupportedOperationException("Can not listen to this class");
    }
}
