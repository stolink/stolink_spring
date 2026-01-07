package com.stolink.backend.domain.project;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
public class UpdateProjectCoversTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("Update all projects with Standard Ebooks covers and titles")
    @Transactional
    @Commit
    void updateAllProjectsWithCovers() {
        List<BookData> books = List.of(
            new BookData("The Haunted Hotel", "Wilkie Collins", "wilkie-collins/the-haunted-hotel"),
            new BookData("The Brooklyn Murders", "G. D. H. Cole", "g-d-h-cole/the-brooklyn-murders"),
            new BookData("The Apple Cart", "George Bernard Shaw", "george-bernard-shaw/the-apple-cart"),
            new BookData("Planet of the Damned", "Harry Harrison", "harry-harrison/planet-of-the-damned"),
            new BookData("The City of God", "Augustine of Hippo", "augustine-of-hippo/the-city-of-god/marcus-dods_george-wilson_j-j-smith"),
            new BookData("As I Lay Dying", "William Faulkner", "william-faulkner/as-i-lay-dying"),
            new BookData("The Faraway Bride", "Stella Benson", "stella-benson/the-faraway-bride"),
            new BookData("Years of Grace", "Margaret Ayer Barnes", "margaret-ayer-barnes/years-of-grace"),
            new BookData("Not Without Laughter", "Langston Hughes", "langston-hughes/not-without-laughter"),
            new BookData("The End of the World", "Geoffrey Dennis", "geoffrey-dennis/the-end-of-the-world"),
            new BookData("The Castle", "Franz Kafka", "franz-kafka/the-castle/willa-muir_edwin-muir"),
            new BookData("Vile Bodies", "Evelyn Waugh", "evelyn-waugh/vile-bodies")
        );

        List<Project> projects = projectRepository.findAll();
        System.out.println("Found " + projects.size() + " projects to update.");

        for (int i = 0; i < projects.size(); i++) {
            Project project = projects.get(i);
            BookData book = books.get(i % books.size());

            String coverUrl = "https://standardebooks.org/ebooks/" + book.slug + "/downloads/cover.jpg";

            project.update(book.title, null, null, book.author, null);
            project.updateCoverImage(coverUrl);

            System.out.println("Updated project: " + project.getId());
            System.out.println("  Title: " + book.title);
            System.out.println("  Author: " + book.author);
            System.out.println("  Cover: " + coverUrl);
        }

        projectRepository.saveAll(projects);
               System.out.println("Successfully updated " + projects.size() + " projects.");
    }

    record BookData(String title, String author, String slug) {}
}
