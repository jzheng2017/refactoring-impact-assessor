package nl.jiankai.refactoring.core.project.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.jiankai.refactoring.core.storage.api.Identifiable;

@JsonIgnoreProperties({"id"})
public record Artifact(Coordinate coordinate, String sourceControlUrl) implements Identifiable {

    @Override
    public String getId() {
        return coordinate.toString();
    }

    public record Coordinate(String groupId, String artifactId, String version) {

        @Override
        public String toString() {
            return groupId + "-" + artifactId + "-" + version;
        }

        public static Coordinate read(String coordinate) {
            String[] split = coordinate.split("/");

            if (split.length != 3) {
                throw new IllegalArgumentException("The provided coordinate does not consist of the three parts!");
            }

            return new Coordinate(split[0], split[1], split[2]);
        }
    }
}
