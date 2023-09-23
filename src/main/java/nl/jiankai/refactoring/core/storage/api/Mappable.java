package nl.jiankai.refactoring.core.storage.api;

public interface Mappable<S, T> {
    T target(S source);
    S source(T target);
}
