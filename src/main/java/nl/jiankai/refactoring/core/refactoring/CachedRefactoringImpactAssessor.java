package nl.jiankai.refactoring.core.refactoring;

import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectListener;
import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectData;
import nl.jiankai.refactoring.serialisation.JacksonSerializationService;
import nl.jiankai.refactoring.core.storage.filestorage.refactoringcache.RefactoringImpactStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CachedRefactoringImpactAssessor implements RefactoringImpactAssessor, ProjectListener<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedRefactoringImpactAssessor.class);
    private Map<RefactoringKey, List<RefactoringImpact>> refactoringImpactCache = new HashMap<>();
    private RefactoringImpactAssessor refactoringImpactAssessor;
    private RefactoringImpactStorageService refactoringImpactStorageService;
    private ApplicationConfiguration applicationConfiguration;
    private ProjectsToScan projectsToScan;

    public CachedRefactoringImpactAssessor(RefactoringImpactAssessor refactoringImpactAssessor) {
        if (CachedRefactoringImpactAssessor.class.equals(refactoringImpactAssessor.getClass())) {
            throw new IllegalArgumentException("Can not inject '%s' into itself!".formatted(refactoringImpactAssessor.getClass()));
        }
        this.projectsToScan = new ProjectsToScan();
        this.refactoringImpactStorageService = new RefactoringImpactStorageService(new JacksonSerializationService());
        this.refactoringImpactAssessor = refactoringImpactAssessor;
        this.applicationConfiguration = new ApplicationConfiguration();
    }

    @Override
    public ImpactAssessment assesImpact(RefactoringData refactoringData) {
        Map<ProjectData, List<RefactoringImpact>> impacts = projectsToScan
                .projects()
                .stream()
                .map(Project::getProjectVersion)
                .map(project -> new ProjectImpactInfo(project, refactoringData, assesImpact(project, refactoringData)))
                .collect(Collectors.toMap(ProjectImpactInfo::projectData, ProjectImpactInfo::refactoringImpacts));

        return new ImpactAssessment(impacts, RefactoringStatisticsGenerator.compute(impacts));
    }

    @Override
    public List<RefactoringImpact> assesImpact(ProjectData projectData, RefactoringData refactoringData) {
        final RefactoringKey refactoringKey = createRefactoringKey(projectData, refactoringData);
        if (isCached(refactoringKey)) {
            LOGGER.info("Project {} is cached. Trying to fetch from cache...", projectData);
            return getFromCache(projectData, refactoringData).orElseGet(() -> {
                LOGGER.warn("Could not find project {} in the cache. The refactoring impact will be recomputed again.", projectData);
                return computeRefactoringImpacts(projectData, refactoringData);
            });
        } else {
            return computeRefactoringImpacts(projectData, refactoringData);
        }
    }

    private boolean isCached(RefactoringKey key) {
        return refactoringImpactCache.containsKey(key) || refactoringImpactStorageService.exists(key.toString());
    }

    private boolean shouldCache(ProjectData project) {
        return !project.coordinate().version().endsWith("-SNAPSHOT");
    }

    private void cacheIfNeeded(ProjectData project, RefactoringData refactoringData, List<RefactoringImpact> refactoringImpacts) {
        if (shouldCache(project)) {
            refactoringImpactCache.put(createRefactoringKey(project, refactoringData), refactoringImpacts);
            refactoringImpactStorageService.write(new ProjectImpactInfo(project, refactoringData, refactoringImpacts));
        }
    }

    private Optional<List<RefactoringImpact>> getFromCache(ProjectData project, RefactoringData refactoringData) {
        final RefactoringKey refactoringKey = createRefactoringKey(project, refactoringData);
        Optional<List<RefactoringImpact>> refactoringImpacts =
                Optional.ofNullable(
                        Optional
                                .ofNullable(refactoringImpactCache.get(refactoringKey)) // try in memory cache first
                                .orElseGet(() -> { //if not in memory then try on disk
                                    LOGGER.info("Refactoring results could not be found in the memory cache. Trying disk cache...");
                                    Optional<ProjectImpactInfo> projectImpactInfo = refactoringImpactStorageService.read(refactoringKey.toString());
                                    projectImpactInfo.ifPresent(p -> LOGGER.info("Refactoring results found on the disk cache!"));
                                    return projectImpactInfo.orElse(new ProjectImpactInfo(project, refactoringData, null)).refactoringImpacts();
                                }));

        refactoringImpacts.ifPresent(p -> LOGGER.info("Found project {} in cache", project));
        return refactoringImpacts;
    }

    private List<RefactoringImpact> computeRefactoringImpacts(ProjectData project, RefactoringData refactoringData) {
        List<RefactoringImpact> refactoringImpacts = refactoringImpactAssessor.assesImpact(project, refactoringData);
        cacheIfNeeded(project, refactoringData, refactoringImpacts);
        return refactoringImpacts;
    }

    @Override
    public void onAdded(ProjectEvent<Project> event) {
        clearCache();
    }

    @Override
    public void onRemoved(ProjectEvent<Project> event) {
        clearCache();
    }

    private void clearCache() {
        refactoringImpactCache.clear();
        refactoringImpactStorageService.clear();
    }

    private RefactoringKey createRefactoringKey(ProjectData project, RefactoringData refactoringData) {
        return new RefactoringKey(project, refactoringData.fullyQualifiedSignature(), refactoringData.refactoringType());
    }

    private record RefactoringKey(ProjectData project, String fullyQualifiedSignature, RefactoringType refactoringType) {

        @Override
        public String toString() {
            return project.toString() + "-" + fullyQualifiedSignature + "-" + refactoringType;
        }
    }
}
