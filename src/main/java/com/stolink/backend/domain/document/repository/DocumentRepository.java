package com.stolink.backend.domain.document.repository;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByProject(Project project);

    List<Document> findByProjectAndParentIsNullOrderByOrder(Project project);

    List<Document> findByParentOrderByOrder(Document parent);

    Optional<Document> findByIdAndProject(UUID id, Project project);

    @Query("SELECT d FROM Document d WHERE d.project = :project AND d.parent IS NULL ORDER BY d.order")
    List<Document> findRootDocuments(@Param("project") Project project);

    @Query("SELECT SUM(d.wordCount) FROM Document d WHERE d.project = :project")
    Long sumWordCountByProject(@Param("project") Project project);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.project = :project AND d.type = 'TEXT'")
    Long countTextDocumentsByProject(@Param("project") Project project);

    @Query("SELECT DISTINCT d FROM Document d LEFT JOIN FETCH d.parent WHERE d.project = :project ORDER BY d.order ASC")
    List<Document> findByProjectWithParent(@Param("project") Project project);

    void deleteAllByProject(Project project);
}
