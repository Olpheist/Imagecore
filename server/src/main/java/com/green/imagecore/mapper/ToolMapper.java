package com.green.imagecore.mapper;

import com.green.imagecore.dto.ToolDto;
import com.green.imagecore.entities.Tool;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapping class for tools to convert to data transport objects
 */
public class ToolMapper {

    /**
     * Tool toDto function to convert tool object to data transport object
     * @param tool the Tool to convert
     * @return ToolDto - tool data transport object
     */
    public static ToolDto toDto(Tool tool) {
        if (tool == null) return null;

        ToolDto dto = new ToolDto();
        dto.setToolId(tool.getToolId());
        dto.setCreatedByUserId(tool.getCreatedBy().getId());
        dto.setName(tool.getName());
        dto.setCategory(tool.getCategory());
        dto.setDescription(tool.getDescription());
        dto.setImageTag(tool.getImageTag());

        return dto;
    }

    /**
     * Tool toDtos function to map a list of Tools to data transport objects
     * @param tools the list of Tools to convert
     * @return List[ToolDto] - list of tool data transport objects
     */
    public static List<ToolDto> toDtos(List<Tool> tools) {
        if (tools == null) return Collections.emptyList();

        return tools.stream()
                .map(ToolMapper::toDto)
                .collect(Collectors.toList());
    }


}
