package nl.jiankai.refactoring.core.storage.filestorage;

import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.api.Identifiable;
import nl.jiankai.refactoring.serialisation.SerializationService;
import nl.jiankai.refactoring.util.HashingUtil;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
    private Class<T> entityClassType;

    public CacheServiceImpl(String baseLocation, SerializationService serializationService, Class<T> entityClassType) {
        this.baseLocation = baseLocation;
        this.serializationService = serializationService;
        this.entityClassType = entityClassType;
    }

    @Override
    public boolean isCached(String identifier) {
        return cache.containsKey(identifier) || new LocalFileStorageService(createFileLocation(identifier), false).exists();
    }

    @Override
    public Optional<T> get(String identifier) {
        if (cache.containsKey(identifier)) {
            LOGGER.info("'{}' is cached. Trying to fetch from cache..", identifier);
            return Optional.of(cache.get(identifier));
        } else {
            LOGGER.info("'{}' is not cached in memory.. Trying to fetch from disk..", identifier);
            LocalFileStorageService fileStorageService = new LocalFileStorageService(createFileLocation(identifier), false);
            if (fileStorageService.exists()) {
                LOGGER.info("'{}' found on the disk cache!", identifier);
                return Optional.of(
                        serializationService.deserialize(
                                fileStorageService.read().collect(Collectors.joining()).getBytes(),
                                entityClassType
                        )
                );
            }
        }

        LOGGER.info("'{}' could not be found in the memory or disk cache..", identifier);
        return Optional.empty();
    }

    @Override
    public void write(T entity) {
        LocalFileStorageService fileStorageService = new LocalFileStorageService(createFileLocation(entity.getId()), true);
        fileStorageService.write(new String(serializationService.serialize(entity)));
        cache.put(entity.getId(), entity);
        LOGGER.warn("Written entity '{}' to the cache", entity.getId());
    }

    @Override
    public void clear() {
        try {
            cache.clear();
            FileUtils.deleteDirectory(new File(baseLocation));
            LOGGER.info("Cache at location '{}' has been cleared", baseLocation);
        } catch (IOException e) {
            LOGGER.warn("Could not clear cache at location '{}'", baseLocation, e);
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
