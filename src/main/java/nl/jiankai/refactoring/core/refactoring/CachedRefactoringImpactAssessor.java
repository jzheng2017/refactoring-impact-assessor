package nl.jiankai.refactoring.core.refactoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.configuration.CacheLocation;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectListener;
import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectData;
import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.api.Identifiable;
import nl.jiankai.refactoring.core.storage.filestorage.MultiFileCacheService;
import nl.jiankai.refactoring.serialisation.JacksonSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CachedRefactoringImpactAssessor implements RefactoringImpactAssessor, ProjectListener<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedRefactoringImpactAssessor.class);
    private RefactoringImpactAssessor refactoringImpactAssessor;
    private CacheService<RefactoringResult> refactoringCacheService;
    private ApplicationConfiguration applicationConfiguration;
    private ProjectsToScan projectsToScan;

    public CachedRefactoringImpactAssessor(RefactoringImpactAssessor refactoringImpactAssessor) {
        if (CachedRefactoringImpactAssessor.class.equals(refactoringImpactAssessor.getClass())) {
            throw new IllegalArgumentException("Can not inject '%s' into itself!".formatted(refactoringImpactAssessor.getClass()));
        }
        this.projectsToScan = new ProjectsToScan();
        this.refactoringImpactAssessor = refactoringImpactAssessor;
        this.refactoringCacheService = new MultiFileCacheService<>(CacheLocation.REFACTORING_IMPACT, new JacksonSerializationService(), RefactoringResult.class);
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
        if (eligibleForCache(projectData)) {
            LOGGER.info("Trying to fetch '{}' from cache..", refactoringKey);
            Optional<RefactoringResult> refactoringResult = refactoringCacheService.get(refactoringKey.toString());

            return refactoringResult.orElseGet(() -> {
                LOGGER.info("Could not find project {} in the cache. The refactoring impact will be recomputed again.", projectData);
                return new RefactoringResult(refactoringKey, computeRefactoringImpactsAndTryCache(projectData, refactoringData));
            }).refactoringResults;
        } else {
            return computeRefactoringImpactsAndTryCache(projectData, refactoringData);
        }
    }
    private boolean eligibleForCache(ProjectData project) {
//        return !project.coordinate().version().endsWith("-SNAPSHOT");
        return true;
    }

    private void cacheIfNeeded(ProjectData project, RefactoringData refactoringData, List<RefactoringImpact> refactoringImpacts) {
        if (eligibleForCache(project)) {
            refactoringCacheService.write(new RefactoringResult(createRefactoringKey(project, refactoringData), refactoringImpacts));
        }
    }

    private List<RefactoringImpact> computeRefactoringImpactsAndTryCache(ProjectData project, RefactoringData refactoringData) {
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
        refactoringCacheService.clear();
    }

    private RefactoringKey createRefactoringKey(ProjectData project, RefactoringData refactoringData) {
        return new RefactoringKey(project, refactoringData.fullyQualifiedSignature(), refactoringData.refactoringType());
    }

    private record RefactoringKey(ProjectData project, String fullyQualifiedSignature,
                                  RefactoringType refactoringType) {

        @Override
        public String toString() {
            return project.toString() + "-" + fullyQualifiedSignature + "-" + refactoringType;
        }
    }

    @JsonIgnoreProperties(value = {"id"})
    private record RefactoringResult(RefactoringKey refactoringKey,
                                     List<RefactoringImpact> refactoringResults) implements Identifiable {

        @Override
        public String getId() {
            return refactoringKey.toString();
        }
    }
}
