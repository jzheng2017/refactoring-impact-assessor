package nl.jiankai.refactoring.core.project.repository;

import java.util.List;
import java.util.Optional;

/**
 * An interface allowing you to talk to a local/remote artifact repository, retrieving various information such as artifacts information.
 */
public interface ArtifactRepository {

    /**
     * Get the artifact matching the provided coordinate if found
     * @param coordinate the artifact coordinate
     * @return the artifact matching the given coordinate
     */
    Optional<Artifact> getArtifact(Artifact.Coordinate coordinate);

    /**
     * Get the artifacts that are using the artifact matching the provided coordinate
     * @param coordinate the artifact coordinate
     * @return a list of artifacts
     */
    List<Artifact> getArtifactUsages(Artifact.Coordinate coordinate);
}