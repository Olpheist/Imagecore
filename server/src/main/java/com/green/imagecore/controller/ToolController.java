package com.green.imagecore.controller;

import com.green.imagecore.dto.ToolDto;
import com.green.imagecore.dto.ToolStatsDto;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.mapper.ToolMapper;
import com.green.imagecore.service.ToolService;
import com.green.imagecore.service.ToolStatsService;
import com.green.imagecore.entities.User;
import com.green.imagecore.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final UserService userService;
    private final ToolStatsService toolStatsService;


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
    @PreAuthorize("hasAnyRole('ADMIN', 'CLINICIAN', 'RESEARCHER')")
    @PostMapping
    public ResponseEntity<ToolDto> createTool(@RequestBody CreateToolRequest request, Authentication authentication) {
        User creatingUser = userService.findByUsername(authentication.getName());
        Tool tool = toolService.create(request.name(), creatingUser, request.category().toLowerCase(), request.description(), request.imageTag(), request.taskDefinitionArn(), request.containerName(), SubscriptionTierCode.FREE);
        return ResponseEntity.ok(ToolMapper.toDto(tool));
    }
    /**
     * DELETE method for removing tools, right now only available to ADMINS
     * @param id the id of the tool to remove
     * @return ResponseEntity the response of the delete
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'CLINICIAN', 'RESEARCHER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTool(@PathVariable Long id, Authentication authentication) {
        toolService.delete(id, authentication);
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

    @PreAuthorize("hasAnyRole('ADMIN', 'CLINICIAN', 'RESEARCHER')")
    @GetMapping("/{id}/stats")
    public ResponseEntity<ToolStatsDto> getToolStats(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(toolStatsService.getStats(id, authentication));
    }

    public record CreateToolRequest(
            String name,
            User createdBy,
            String category,
            String description,
            String imageTag,
            String taskDefinitionArn,
            String containerName
    ) {}
}