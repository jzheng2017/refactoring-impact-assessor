package nl.jiankai.refactoring.core.refactoring;

import java.util.List;

public record RefactoringStatistics(List<Ranking> projectsWithMostImpact,
                                    List<Ranking> filesWithMostImpacts,
                                    long projectsImpacted

                                    ) {

    @Override
    public String toString() {
        int mostImpactLimit = 3;
        StringBuilder view = new StringBuilder();
        view
                .append("Top %s projects with most impacts".formatted(mostImpactLimit))
                .append("\n=================================\n");
        for (Ranking ranking : projectsWithMostImpact.stream().limit(3).toList()) {
            view
                    .append(ranking.name)
                    .append(": ")
                    .append(ranking.value)
                    .append("\n");
        }

        view
                .append("\n=================================\n")
                .append("Top %s files with most impacts".formatted(mostImpactLimit))
                .append("\n=================================\n");

        for (Ranking ranking : filesWithMostImpacts.stream().limit(3).toList()) {
            view
                    .append(ranking.name)
                    .append(": ")
                    .append(ranking.value)
                    .append("\n");
        }

        view
                .append("\n=================================\n")
                .append("%s projects out of %s impacted".formatted(projectsImpacted, projectsWithMostImpact.size()))
                .append("\n=================================\n");

        return view.toString();
    }

    record Ranking(String name, int value) {}
}
