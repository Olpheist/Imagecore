package com.green.imagecore.service;

import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.ToolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service layer for managing the medical imaging tool catalog.
 *
 * Provides operations for creating, retrieving, and deleting tools.
 * Each tool represents a containerized medical imaging algorithm stored
 * in Amazon ECR, identified by its image tag for downstream dispatch.
 */
@Service
@RequiredArgsConstructor
public class ToolService {
    private final ToolRepository toolRepository;

    /**
     * Creates and persists a new medical imaging tool.
     *
     * @param name        unique display name for the tool (e.g. "brain-segmentation")
     * @param category    functional grouping of the tool (e.g. "segmentation", "detection")
     * @param description human-readable summary of what the tool does, may be null
     * @param imageTag    fully-qualified docker image URI used to pull and run the
     *                    tool's Docker container, may be null if not yet deployed
     * @return the persisted {@link Tool} with its database-assigned ID
     * @throws IllegalArgumentException if a tool with the given name already exists
     */
    public Tool create(String name, User createdBy, String category, String description, String imageTag) {
        if (toolRepository.existsByName(name)) {
            throw new IllegalArgumentException("A tool with name '" + name + "' already exists.");
        }

        Tool tool = new Tool();
        tool.setName(name);
        tool.setCreatedBy(createdBy);
        tool.setCategory(category);
        tool.setDescription(description);
        tool.setImageTag(imageTag);

        return toolRepository.save(tool);
    }

    /**
     * Retrieves all tools in the catalog regardless of category.
     *
     * @return list of all persisted {@link Tool} entities, empty list if none exist
     */
    @Transactional(readOnly = true)
    public List<Tool> findAll() {
        return toolRepository.findAll();
    }

    /**
     * Deletes a tool by its database ID.
     *
     * @param id the unique identifier of the tool to remove
     * @throws ResourceNotFoundException if no tool exists with the given ID
     */
    @Transactional
    public void delete(Long id, Authentication auth) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found with id: " + id));

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));

        boolean isOwner = tool.getCreatedBy().getUsername().equals(auth.getName());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You do not have permission to delete this tool.");
        }

        toolRepository.delete(tool);
    }

    /**
     * Finds a single tool by its unique name.
     *
     * @param toolName the unique name of the tool to look up
     * @return the matching {@link Tool}
     * @throws ResourceNotFoundException if no tool exists with the given name
     */
    @Transactional(readOnly = true)
    public Tool findByName(String toolName) {
        return toolRepository.findByName(toolName)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found with name: " + toolName));
    }

    /**
     * Retrieves all tools belonging to a given category.
     * Intended for frontend use to group and display tools by functional type.
     *
     * @param category the category to filter by (e.g. "segmentation", "detection")
     * @return list of {@link Tool} entities in the given category, empty list if none exist
     */
    @Transactional(readOnly = true)
    public List<Tool> findByCategory(String category) {
        return toolRepository.findByCategory(category);
    }
}