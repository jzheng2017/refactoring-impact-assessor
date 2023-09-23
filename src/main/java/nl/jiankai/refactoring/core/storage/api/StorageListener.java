package nl.jiankai.refactoring.core.storage.api;

public interface StorageListener<T> {

    void onAdded(StorageEvent<T> event);
    void onUpdated(StorageEvent<T> event);
    void onRemoved(StorageEvent<T> event);

    record StorageEvent<T>(T affected){}
}
