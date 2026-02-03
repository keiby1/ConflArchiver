package com.example.ConflArchReport.controller;

import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.service.ArchivedReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReportViewController {

    private final ArchivedReportService archivedReportService;
    private final ObjectMapper objectMapper;

    @Value("${jira.url:}")
    private String jiraUrl;

    public ReportViewController(ArchivedReportService archivedReportService) {
        this.archivedReportService = archivedReportService;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(required = false) String project,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {

        List<String> projects = archivedReportService.getAllProjectNames();
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProject", project != null ? project : "");
        model.addAttribute("searchTerm", search != null ? search : "");
        model.addAttribute("jiraBaseUrl", jiraUrl != null ? jiraUrl : "");

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<ArchivedReport> reportsPage = archivedReportService.searchArchives(project, search, pageable);
        List<ArchivedReport> reports = reportsPage.getContent();

        model.addAttribute("reports", reports);
        model.addAttribute("reportJsonInfo", buildReportJsonInfo(reports));
        model.addAttribute("currentPage", reportsPage.getNumber());
        model.addAttribute("totalPages", reportsPage.getTotalPages());
        model.addAttribute("totalItems", reportsPage.getTotalElements());
        model.addAttribute("hasNext", reportsPage.hasNext());
        model.addAttribute("hasPrevious", reportsPage.hasPrevious());
        model.addAttribute("pageSize", size);

        return "index";
    }

    private Map<Long, String> buildReportJsonInfo(List<ArchivedReport> reports) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (ArchivedReport r : reports) {
            if (r.getJsonInfo() != null && !r.getJsonInfo().isEmpty()) {
                try {
                    result.put(r.getPk(), objectMapper.writeValueAsString(r.getJsonInfo()));
                } catch (JsonProcessingException e) {
                    result.put(r.getPk(), "{}");
                }
            }
        }
        return result;
    }
}
