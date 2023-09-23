package nl.jiankai.refactoring.storage.api;

import java.util.List;
import java.util.stream.Stream;

public interface StorageService<T> extends StorageObservable<T> {
    /**
     * Reads the whole content containing type T and returns a stream of it
     * @return a stream of type T
     */
    Stream<T> read();

    /**
     * Write object of type T to the storage (this is not an append but overwrites the storage)
     * @param content object of type T
     */
    void write(T content);

    /**
     * Write a list of type T to the storage (this is not an append but overwrites the storage)
     * @param list list of objects of type T
     */
    void write(List<T> list);

    /**
     * Append an object of type T to the storage
     * @param content object of type T
     */
    void append(T content);

    /**
     * Append a list containing objects of type T to the storage
     * @param list list of objects of type T
     */
    void append(List<T> list);

    /**
     * Empties the whole store by clearing all entries
     */
    void clear();
}
