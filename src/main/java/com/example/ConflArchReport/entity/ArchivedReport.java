package com.example.ConflArchReport.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "archived_reports", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "archive_id"})
})
public class ArchivedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pk;

    /**
     * Идентификатор архива - имя архива без расширения .zip (уникален в рамках проекта)
     */
    @Column(name = "archive_id", nullable = false, length = 500)
    private String id;

    @Column(nullable = false, length = 1000)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "jira_key", length = 50)
    private String jiraKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "json_info", columnDefinition = "jsonb")
    private Map<String, Object> jsonInfo;

    public ArchivedReport() {
    }

    public ArchivedReport(String id, String name, Project project) {
        this.id = id;
        this.name = name;
        this.project = project;
    }

    public Long getPk() {
        return pk;
    }

    public void setPk(Long pk) {
        this.pk = pk;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getJiraKey() {
        return jiraKey;
    }

    public void setJiraKey(String jiraKey) {
        this.jiraKey = jiraKey;
    }

    public Map<String, Object> getJsonInfo() {
        return jsonInfo;
    }

    public void setJsonInfo(Map<String, Object> jsonInfo) {
        this.jsonInfo = jsonInfo;
    }
}
