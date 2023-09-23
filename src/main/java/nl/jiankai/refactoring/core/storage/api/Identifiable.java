package nl.jiankai.refactoring.core.storage.api;

/**
 * A class that implements this interface can be identified by a unique id
 */
public interface Identifiable {
    /**
     * @return a string that uniquely identifies the entity
     */
    String getId();
}
