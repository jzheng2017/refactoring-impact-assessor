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
import java.util.*;
import java.util.stream.Collectors;

public class MavenCentralRepository implements ArtifactRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCentralRepository.class);
    private SerializationService serializationService = new JacksonSerializationService();
    private CacheService<Artifact> artifactCacheService = new MultiFileCacheService<>(CacheLocation.ARTIFACTS, serializationService, Artifact.class);

    @Override
    public Optional<Artifact> getArtifact(Artifact.Coordinate coordinate) {
        try {
            LOGGER.info("Fetching artifact information for {}", coordinate);
            Document document = Jsoup.connect("https://central.sonatype.com/artifact/%s/%s/%s".formatted(coordinate.groupId(), coordinate.artifactId(), coordinate.version())).get();
            Elements elements = document.getElementsByAttributeValue("data-test", "scm-url");
            if (!elements.isEmpty()) {
                String githubLink = elements.get(0).attr("href");

                return Optional.of(new Artifact(coordinate, stripUnnecessaryPart(githubLink)));
            } else {
                LOGGER.warn("Artifact {} has no source control link", coordinate);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not get the artifact information of '{}'", coordinate, e);
        }

        return Optional.empty();
    }

    private String stripUnnecessaryPart(String githubLink) {
        if (githubLink == null) {
            return null;
        }

        if (githubLink.startsWith("scm@")) {
            githubLink = githubLink.substring("scm@".length());
            githubLink = !githubLink.startsWith("http://") ? "http://" + githubLink : githubLink;
        } else if (githubLink.startsWith("scm:git@")) {
            githubLink = githubLink.substring("scm:git@".length());
            githubLink = !githubLink.startsWith("http://") ? "http://" + githubLink : githubLink;
        }

        githubLink = githubLink.replace(".git", "");
        githubLink = githubLink.replaceAll(".com:", ".com/");

        return githubLink;
    }

    @Override
    public List<Artifact> getArtifactUsages(Artifact.Coordinate coordinate, PageOptions pageOptions, FilterOptions filterOptions) {
        List<Artifact> artifacts = new ArrayList<>();
        try {
            Set<GroupAndArtifactId> alreadyFoundArtifacts =
                    filterOptions
                            .ignoreArtifacts()
                            .stream()
                            .map(artifact -> new GroupAndArtifactId(artifact.groupId(), artifact.artifactId()))
                            .collect(Collectors.toSet());
            String body = post(coordinate, pageOptions);
            Map<String, Object> usages = serializationService.read(body.getBytes());
            List<Map<String, String>> components = (List<Map<String, String>>) usages.get("components");
            if (components != null) {
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
                        GroupAndArtifactId groupAndArtifactId = new GroupAndArtifactId(artifactCoordinate.groupId(), artifactCoordinate.artifactId());

                        if (!alreadyFoundArtifacts.contains(groupAndArtifactId)) {
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
                            alreadyFoundArtifacts.add(groupAndArtifactId);
                        } else {
                            LOGGER.info("Skipped: Artifact '{}' has already been added", groupAndArtifactId);
                            artifacts.add(new Artifact(new Artifact.Coordinate("", "", ""), null)); //to indicate there were artifacts found but not usable to make the script keep running
                        }
                    } else {
                        artifacts.add(new Artifact(new Artifact.Coordinate("", "", ""), null)); //to indicate there were artifacts found but not usable to make the script keep running
                    }
                }
            }
            return artifacts.stream().map(artifact -> new Artifact(artifact.coordinate(), stripUnnecessaryPart(artifact.sourceControlUrl()))).toList();
        } catch (Exception e) {
            LOGGER.error("Something went wrong", e);
            return artifacts;
        }
    }

    private String post(Artifact.Coordinate coordinate, PageOptions pageOptions) {
        String url = "https://central.sonatype.com/api/internal/browse/dependents";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .headers(
                        "Content-Type", "application/json",
                        "Accept", "*/*",
                        "User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/117.0",
                        "Origin", "https://central.sonatype.com"
                )
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
            LOGGER.info("Sending http post request to retrieve artifact dependents with arguments: page number {} and page size {}", pageOptions.page(), pageOptions.pageSize());
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException e) {
            LOGGER.warn("Something went wrong while retrieving the dependents", e);
            throw new RuntimeException(e);
        }
    }

    private record DependentsRequestBody(String purl, int page, int size, String searchTerm, String[] filter) {
    }

    private record GroupAndArtifactId(String groupId, String artifactId) {
        @Override
        public String toString() {
            return groupId + "-" + artifactId;
        }
    }
}
