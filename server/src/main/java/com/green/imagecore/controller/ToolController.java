package com.green.imagecore.controller;

import com.green.imagecore.dto.ToolDto;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.mapper.ToolMapper;
import com.green.imagecore.service.ToolService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Rest Controller for tools
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/tools")
public class ToolController {
    private final ToolService toolService;


    /**
     * GET method for returning all tools, only accessible through clinicians right now
     * @return List<ToolDto> a list of all tools as data transport objects
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CLINICIAN', 'RESEARCHER')")
    @GetMapping
    public List<ToolDto> getAllTools() {
        return toolService.findAll()
                .stream()
                .map(ToolMapper::toDto)
                .toList();
    }

    /**
     * POST method for creating a tool, right now only available to ADMINS, will possibly stay like this
     * @param request a CreateToolRequest
     * @return ResponseEntity showing the response of the tool creation
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'RESEARCHER')")
    @PostMapping
    public ResponseEntity<ToolDto> createTool(@RequestBody CreateToolRequest request) {
        Tool tool = toolService.create(request.name(), request.category().toLowerCase(), request.description(), request.imageTag());
        return ResponseEntity.ok(ToolMapper.toDto(tool));
    }

    /**
     * DELETE method for removing tools, right now only available to ADMINS
     * @param id the id of the tool to remove
     * @return ResponseEntity the response of the delete
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CLINICIAN', 'RESEARCHER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTool(@PathVariable Long id) {
        toolService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'CLINICIAN', 'RESEARCHER')")
    @GetMapping("/category/{category}")
    public List<ToolDto> getToolsByCategory(@PathVariable String category) {
        return toolService.findByCategory(category)
                .stream()
                .map(ToolMapper::toDto)
                .toList();
    }

    public record CreateToolRequest(
            String name,
            String category,
            String description,
            String imageTag
    ) {}
}