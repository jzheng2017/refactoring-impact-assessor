package nl.jiankai.refactoring.storage.api;

public interface Mappable<S, T> {
    T target(S source);
    S source(T target);
}
