package nl.jiankai.refactoring.core.storage.filestorage;

import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.api.EntityStorageService;
import nl.jiankai.refactoring.core.storage.api.Identifiable;

import java.util.Optional;

public class CacheServiceImpl<T extends Identifiable> implements CacheService<T> {
    private final String baseLocation;
    private EntityStorageService<T> entityStorageService;
    public CacheServiceImpl(String baseLocation, EntityStorageService<T> entityStorageService) {
        this.baseLocation = baseLocation;
        this.entityStorageService = entityStorageService;
    }

    @Override
    public boolean isCached(String identifier) {
        return entityStorageService.exists(identifier);
    }

    @Override
    public Optional<T> get(String identifier) {
        return entityStorageService.read(identifier);
    }

    @Override
    public void write(T entity) {
        entityStorageService.write(entity);
    }
}
