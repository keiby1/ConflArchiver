package com.example.ConflArchReport.repository;

import com.example.ConflArchReport.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByName(String name);

    List<Project> findByNameContainingIgnoreCase(String searchTerm);
}
