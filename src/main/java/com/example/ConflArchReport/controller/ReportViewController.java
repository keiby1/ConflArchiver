package com.example.ConflArchReport.controller;

import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.service.ArchivedReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ReportViewController {

    private final ArchivedReportService archivedReportService;

    public ReportViewController(ArchivedReportService archivedReportService) {
        this.archivedReportService = archivedReportService;
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

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<ArchivedReport> reportsPage = archivedReportService.searchArchives(project, search, pageable);

        model.addAttribute("reports", reportsPage.getContent());
        model.addAttribute("currentPage", reportsPage.getNumber());
        model.addAttribute("totalPages", reportsPage.getTotalPages());
        model.addAttribute("totalItems", reportsPage.getTotalElements());
        model.addAttribute("hasNext", reportsPage.hasNext());
        model.addAttribute("hasPrevious", reportsPage.hasPrevious());
        model.addAttribute("pageSize", size);

        return "index";
    }
}
