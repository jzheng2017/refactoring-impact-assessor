package nl.jiankai.refactoring.core.project.repository;

public record Artifact(Coordinate coordinate, String sourceControlUrl) {

    public record Coordinate(String groupId, String artifactId, String version) {
    }
}
