package nl.jiankai.refactoring.core.storage.filestorage;

import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.api.Identifiable;
import nl.jiankai.refactoring.serialisation.SerializationService;
import nl.jiankai.refactoring.util.HashingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CacheServiceImpl<T extends Identifiable> implements CacheService<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheServiceImpl.class);
    private final String baseLocation;
    private SerializationService serializationService;
    private Map<String, T> cache = new HashMap<>();
    private Class<T> clazz;

    public CacheServiceImpl(String baseLocation, SerializationService serializationService) {
        this.baseLocation = baseLocation;
        this.serializationService = serializationService;
    }

    @Override
    public boolean isCached(String identifier) {
        return cache.containsKey(identifier) || new LocalFileStorageService(createFileLocation(identifier), false).exists();
    }

    @Override
    public Optional<T> get(String identifier) {
        if (cache.containsKey(identifier)) {
            return Optional.of(cache.get(identifier));
        } else {
            LocalFileStorageService fileStorageService = new LocalFileStorageService(createFileLocation(identifier), false);
            if (fileStorageService.exists()) {
                return Optional.of(
                        serializationService.deserialize(
                                fileStorageService.read().collect(Collectors.joining()).getBytes(),
                                clazz
                        )
                );
            }
        }

        return Optional.empty();
    }

    @Override
    public void write(T entity) {
        LocalFileStorageService fileStorageService = new LocalFileStorageService(createFileLocation(entity.getId()), true);
        fileStorageService.write(new String(serializationService.serialize(entity)));
        cache.put(entity.getId(), entity);
    }

    @Override
    public void clear() {
        if (new File(baseLocation).delete()) {
            LOGGER.info("Cache at location '{}' has been cleared", baseLocation);
        } else {
            LOGGER.warn("Could not clear cache as location '{}'", baseLocation);
        }
    }


    private String createFileLocation(String filename) {
        try {
            return baseLocation + File.separator + HashingUtil.md5Hash(filename);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warn("Couldn't hash the filename due to the hashing algorithm not being present", e);
            throw new IllegalStateException(e);
        }
    }
}
