package com.green.imagecore.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.green.imagecore.entities.Tool;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Tool} persistence operations.
 *
 * Extends {@link JpaRepository} for standard CRUD operations.
 * Custom query methods below are auto-implemented by Spring Data JPA
 * from their method names — no SQL required.
 */
public interface ToolRepository extends JpaRepository<Tool, Long> {

    /** Returns true if a tool with the given name already exists. Used by create() to enforce uniqueness. */
    boolean existsByName(String name);

    /** Finds a tool by its unique name, returning empty if not found. */
    Optional<Tool> findByName(String name);

    /** Returns all tools belonging to the given category, empty list if none exist. */
    List<Tool> findByCategory(String category);
}