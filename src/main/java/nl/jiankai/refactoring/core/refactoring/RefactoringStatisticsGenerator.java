package nl.jiankai.refactoring.core.refactoring;

import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectData;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class RefactoringStatisticsGenerator {
    public static RefactoringStatistics compute(Map<ProjectData, List<RefactoringImpact>> projectImpactInfo) {
        return new RefactoringStatistics(computeProjectsWithMostImpact(projectImpactInfo), computeMostImpactedFiles(projectImpactInfo.values().stream().flatMap(Collection::stream).toList()), computeTotalProjectsImpacted(projectImpactInfo));
    }

    private static long computeTotalProjectsImpacted(Map<ProjectData, List<RefactoringImpact>> projectImpactInfo) {
        return projectImpactInfo
                .values()
                .stream()
                .filter(Predicate.not(List::isEmpty))
                .count();
    }

    private static List<RefactoringStatistics.Ranking> computeProjectsWithMostImpact(Map<ProjectData, List<RefactoringImpact>> projectImpactInfo) {
        List<RefactoringStatistics.Ranking> projects = projectImpactInfo.entrySet().stream().map(entry -> new RefactoringStatistics.Ranking(entry.getKey().toString(), entry.getValue().size())).collect(Collectors.toList());

        projects.sort(Comparator.comparingInt(RefactoringStatistics.Ranking::value).reversed());

        return projects;
    }

    private static List<RefactoringStatistics.Ranking> computeMostImpactedFiles(List<RefactoringImpact> refactoringImpacts) {
        Map<String, Integer> files = new HashMap<>();

        for (RefactoringImpact refactoringImpact : refactoringImpacts) {
            String filePath = refactoringImpact.filePath();
            if (files.containsKey(filePath)) {
                files.put(filePath, files.get(filePath) + 1);
            } else {
                files.put(filePath, 1);
            }
        }

        List<RefactoringStatistics.Ranking> filesList = files.entrySet().stream().map(entry -> new RefactoringStatistics.Ranking(entry.getKey(), entry.getValue())).collect(Collectors.toList());

        filesList.sort(Comparator.comparingInt(RefactoringStatistics.Ranking::value).reversed());

        return filesList;
    }
}
