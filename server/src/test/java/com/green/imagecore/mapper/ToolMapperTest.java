package com.green.imagecore.mapper;

import com.green.imagecore.dto.ToolDto;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolMapperTest {

    // Helpers

    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Tool buildTool(Long toolId, Long userId, String name, String category,
                           String description, String imageTag) {
        Tool tool = new Tool();
        tool.setToolId(toolId);
        tool.setCreatedBy(buildUser(userId));
        tool.setName(name);
        tool.setCategory(category);
        tool.setDescription(description);
        tool.setImageTag(imageTag);
        return tool;
    }

    // toDto

    @Test
    void toDto_nullTool_returnsNull() {
        assertNull(ToolMapper.toDto(null));
    }

    @Test
    void toDto_validTool_mapsAllFieldsCorrectly() {
        Tool tool = buildTool(1L, 42L, "Hammer", "Hand Tools", "A basic hammer", "hammer:latest");

        ToolDto dto = ToolMapper.toDto(tool);

        assertNotNull(dto);
        assertEquals(1L, dto.getToolId());
        assertEquals(42L, dto.getCreatedByUserId());
        assertEquals("Hammer", dto.getName());
        assertEquals("Hand Tools", dto.getCategory());
        assertEquals("A basic hammer", dto.getDescription());
        assertEquals("hammer:latest", dto.getImageTag());
    }

    @Test
    void toDto_toolWithNullOptionalFields_mapsWithoutException() {
        Tool tool = new Tool();
        tool.setToolId(2L);
        tool.setCreatedBy(buildUser(10L));
        tool.setName(null);
        tool.setCategory(null);
        tool.setDescription(null);
        tool.setImageTag(null);

        ToolDto dto = ToolMapper.toDto(tool);

        assertNotNull(dto);
        assertEquals(2L, dto.getToolId());
        assertEquals(10L, dto.getCreatedByUserId());
        assertNull(dto.getName());
        assertNull(dto.getCategory());
        assertNull(dto.getDescription());
        assertNull(dto.getImageTag());
    }

    @Test
    void toDto_doesNotMutateOriginalTool() {
        Tool tool = buildTool(5L, 99L, "Wrench", "Hand Tools", "Adjustable wrench", "wrench:v1");

        ToolMapper.toDto(tool);

        assertEquals(5L, tool.getToolId());
        assertEquals(99L, tool.getCreatedBy().getId());
        assertEquals("Wrench", tool.getName());
    }

    // toDtos

    @Test
    void toDtos_nullList_returnsEmptyList() {
        assertTrue(ToolMapper.toDtos(null).isEmpty());
    }

    @Test
    void toDtos_emptyList_returnsEmptyList() {
        assertTrue(ToolMapper.toDtos(List.of()).isEmpty());
    }

    @Test
    void toDtos_validList_mapsAllTools() {
        Tool tool1 = buildTool(1L, 10L, "Hammer", "Hand Tools", "A hammer", "hammer:latest");
        Tool tool2 = buildTool(2L, 20L, "Drill", "Power Tools", "A drill", "drill:latest");

        List<ToolDto> dtos = ToolMapper.toDtos(List.of(tool1, tool2));

        assertEquals(2, dtos.size());

        assertEquals(1L, dtos.get(0).getToolId());
        assertEquals(10L, dtos.get(0).getCreatedByUserId());
        assertEquals("Hammer", dtos.get(0).getName());

        assertEquals(2L, dtos.get(1).getToolId());
        assertEquals(20L, dtos.get(1).getCreatedByUserId());
        assertEquals("Drill", dtos.get(1).getName());
    }

    @Test
    void toDtos_singleItemList_returnsOneDto() {
        Tool tool = buildTool(3L, 15L, "Screwdriver", "Hand Tools", "Flathead", "screwdriver:v2");

        List<ToolDto> dtos = ToolMapper.toDtos(List.of(tool));

        assertEquals(1, dtos.size());
        assertEquals(3L, dtos.get(0).getToolId());
        assertEquals("Screwdriver", dtos.get(0).getName());
    }

    @Test
    void toDtos_listContainingNullTool_includesNullInResult() {
        Tool tool = buildTool(1L, 10L, "Hammer", "Hand Tools", "A hammer", "hammer:latest");
        List<Tool> tools = new java.util.ArrayList<>();
        tools.add(tool);
        tools.add(null);

        List<ToolDto> dtos = ToolMapper.toDtos(tools);

        assertEquals(2, dtos.size());
        assertNotNull(dtos.get(0));
        assertNull(dtos.get(1));
    }

    @Test
    void toDtos_preservesOrderOfInputList() {
        Tool tool1 = buildTool(1L, 10L, "Alpha", "Cat A", "First", "alpha:v1");
        Tool tool2 = buildTool(2L, 20L, "Beta", "Cat B", "Second", "beta:v1");
        Tool tool3 = buildTool(3L, 30L, "Gamma", "Cat C", "Third", "gamma:v1");

        List<ToolDto> dtos = ToolMapper.toDtos(List.of(tool1, tool2, tool3));

        assertEquals("Alpha", dtos.get(0).getName());
        assertEquals("Beta", dtos.get(1).getName());
        assertEquals("Gamma", dtos.get(2).getName());
    }
}