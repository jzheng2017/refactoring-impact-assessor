package nl.jiankai.refactoring.storage.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EntityStorageService<E extends Identifiable> extends StorageService<E> {
    Optional<E> read(String identifier);
    boolean exists(String identifier);
    void remove(E entity);
    void remove(String identifier);

    void remove(Collection<E> entities);
    void remove(List<String> identifiers);
}
