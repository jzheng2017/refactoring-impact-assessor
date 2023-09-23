package nl.jiankai.refactoring.core.project.repository.maven;

import nl.jiankai.refactoring.core.project.repository.Artifact;
import nl.jiankai.refactoring.core.project.repository.ArtifactRepository;

import java.util.List;
import java.util.Optional;

public class MavenCentralRepository implements ArtifactRepository {
    @Override
    public Optional<Artifact> getArtifact(Artifact.Coordinate coordinate) {
        return Optional.empty();
    }

    @Override
    public List<Artifact> getArtifactUsages(Artifact.Coordinate coordinate) {
        return null;
    }
}
