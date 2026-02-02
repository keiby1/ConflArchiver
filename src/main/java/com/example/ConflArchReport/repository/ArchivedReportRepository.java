package com.example.ConflArchReport.repository;

import com.example.ConflArchReport.entity.ArchivedReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArchivedReportRepository extends JpaRepository<ArchivedReport, Long> {

    List<ArchivedReport> findByProjectName(String projectName);

    Optional<ArchivedReport> findByProjectNameAndId(String projectName, String id);

    @Query("SELECT ar FROM ArchivedReport ar WHERE ar.project.name = :projectName " +
           "AND (LOWER(ar.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(ar.id) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR (ar.jiraKey IS NOT NULL AND LOWER(ar.jiraKey) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<ArchivedReport> findByProjectAndSearch(@Param("projectName") String projectName,
                                                 @Param("search") String search,
                                                 Pageable pageable);

    @Query("SELECT ar FROM ArchivedReport ar WHERE " +
           "(LOWER(ar.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(ar.id) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR (ar.jiraKey IS NOT NULL AND LOWER(ar.jiraKey) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<ArchivedReport> findBySearch(@Param("search") String search, Pageable pageable);

    @Query("SELECT ar FROM ArchivedReport ar WHERE ar.project.name = :projectName")
    Page<ArchivedReport> findByProject(@Param("projectName") String projectName, Pageable pageable);

    @Query("SELECT ar FROM ArchivedReport ar WHERE ar.project.name IN :projectNames")
    Page<ArchivedReport> findByProjects(@Param("projectNames") List<String> projectNames, Pageable pageable);

    @Query("SELECT ar FROM ArchivedReport ar WHERE ar.project.name IN :projectNames " +
           "AND (LOWER(ar.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(ar.id) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR (ar.jiraKey IS NOT NULL AND LOWER(ar.jiraKey) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<ArchivedReport> findByProjectsAndSearch(@Param("projectNames") List<String> projectNames,
                                                  @Param("search") String search,
                                                  Pageable pageable);
}
