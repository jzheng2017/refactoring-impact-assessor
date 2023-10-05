package nl.jiankai.refactoring.core.project.repository.maven;

import nl.jiankai.refactoring.configuration.CacheLocation;
import nl.jiankai.refactoring.core.project.repository.Artifact;
import nl.jiankai.refactoring.core.project.repository.ArtifactRepository;
import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.filestorage.MultiFileCacheService;
import nl.jiankai.refactoring.serialisation.JacksonSerializationService;
import nl.jiankai.refactoring.serialisation.SerializationService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MavenCentralRepository implements ArtifactRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCentralRepository.class);
    private SerializationService serializationService = new JacksonSerializationService();
    private CacheService<Artifact> artifactCacheService = new MultiFileCacheService<>(CacheLocation.ARTIFACTS, serializationService, Artifact.class);

    @Override
    public Optional<Artifact> getArtifact(Artifact.Coordinate coordinate) {
        try {
            Document document = Jsoup.connect("https://central.sonatype.com/artifact/%s/%s/%s".formatted(coordinate.groupId(), coordinate.artifactId(), coordinate.version())).get();
            Elements elements = document.getElementsByAttributeValue("data-test", "scm-url");
            if (!elements.isEmpty()) {
                String githubLink = elements.get(0).attr("href");

                return Optional.of(new Artifact(coordinate, githubLink));
            } else {
                LOGGER.warn("Artifact {} has no source control link", coordinate);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not get the artifact information of '{}'", coordinate, e);
        }

        return Optional.empty();
    }

    @Override
    public List<Artifact> getArtifactUsages(Artifact.Coordinate coordinate, PageOptions pageOptions) {
        try {
            List<Artifact> artifacts = new ArrayList<>();
            String body = post(coordinate, pageOptions);
            Map<String, Object> usages = serializationService.read(body.getBytes());
            List<Map<String, String>> components = (List<Map<String, String>>) usages.get("components");
            LOGGER.info("{} artifacts found that are depending on {}", components.size(), coordinate);
            for (Map<String, String> component : components) {
                if (component.containsKey("sourcePurl")) {
                    String sourcePurl = component.get("sourcePurl");
                    String componentCoordinate;

                    if (sourcePurl.startsWith("pkg:maven/")) {
                        componentCoordinate = sourcePurl.substring("pkg:maven/".length());
                    } else {
                        componentCoordinate = sourcePurl;
                    }

                    Artifact.Coordinate artifactCoordinate = Artifact.Coordinate.read(componentCoordinate.replaceAll("@", "/"));
                    String artifactId = artifactCoordinate.toString();
                    if (artifactCacheService.isCached(artifactId)) {
                        Optional<Artifact> artifactOptional = artifactCacheService.get(artifactId);
                        artifactOptional.ifPresentOrElse(artifacts::add, () -> LOGGER.warn("Something went wrong while retrieving {} from the cache", artifactId));
                    } else {
                        Optional<Artifact> artifactOptional = getArtifact(artifactCoordinate);
                        artifactOptional.ifPresent(artifact -> {
                            artifactCacheService.write(artifact);
                            artifacts.add(artifact);
                        });
                    }
                }
            }
            return artifacts;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String post(Artifact.Coordinate coordinate, PageOptions pageOptions) {
        String url = "https://central.sonatype.com/api/internal/browse/dependents";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .headers("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                                serializationService.serialize(
                                        new DependentsRequestBody(
                                                "pkg:maven/%s/%s@%s".formatted(coordinate.groupId(), coordinate.artifactId(), coordinate.version()),
                                                pageOptions.page(),
                                                pageOptions.pageSize(),
                                                "",
                                                new String[]{"dependencyRef:DIRECT"}
                                        )
                                )
                        )
                )
                .build();

        try {
            LOGGER.info("Sending http post request to retrieve artifact dependents");
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException e) {
            LOGGER.warn("Something went wrong while retrieving the dependents", e);
            throw new RuntimeException(e);
        }
    }

    private record DependentsRequestBody(String purl, int page, int size, String searchTerm, String[] filter) {
    }
}
