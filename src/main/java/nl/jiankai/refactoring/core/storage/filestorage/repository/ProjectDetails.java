package nl.jiankai.refactoring.core.storage.filestorage.repository;

import nl.jiankai.refactoring.core.storage.api.Identifiable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ProjectDetails implements Identifiable {
    private final String url;
    private String urlPath;

    public ProjectDetails(String url) {
        this.url = url;
        try {
            this.urlPath = Arrays
                    .stream(
                            new URL(url)
                                    .getPath()
                                    .split("/")
                    )
                    .filter(Predicate.not(String::isBlank))
                    .collect(Collectors.joining("-"));
        } catch (MalformedURLException e) {
            this.urlPath = "";
        }
    }

    @Override
    public String toString() {
        return url;
    }

    public String url() {
        return url;
    }

    public String urlPath() {
        return urlPath;
    }

    @Override
    public String getId() {
        return urlPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectDetails that = (ProjectDetails) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
