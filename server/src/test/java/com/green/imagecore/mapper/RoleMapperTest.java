package com.green.imagecore.mapper;

import com.green.imagecore.dto.RoleDto;
import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.RoleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleMapperTest {

    // --- toDto ---

    @Test
    void toDto_nullRole_returnsNull() {
        assertNull(RoleMapper.toDto(null));
    }

    @Test
    void toDto_validRole_mapsCorrectly() {
        Role role = new Role();
        role.setId(1L);
        role.setName(RoleType.ADMIN);

        RoleDto dto = RoleMapper.toDto(role);

        assertEquals(1L, dto.getId());
        assertEquals("ADMIN", dto.getName());
    }

    @Test
    void toDto_allRoleTypes_mapCorrectly() {
        for (RoleType type : RoleType.values()) {
            Role role = new Role();
            role.setId(1L);
            role.setName(type);

            RoleDto dto = RoleMapper.toDto(role);

            assertEquals(type.name(), dto.getName());
        }
    }

    // --- toDtos ---

    @Test
    void toDtos_nullList_returnsEmptyList() {
        assertTrue(RoleMapper.toDtos(null).isEmpty());
    }

    @Test
    void toDtos_emptyList_returnsEmptyList() {
        assertTrue(RoleMapper.toDtos(List.of()).isEmpty());
    }

    @Test
    void toDtos_validList_mapsAllRoles() {
        Role admin = new Role();
        admin.setId(1L);
        admin.setName(RoleType.ADMIN);

        Role clinician = new Role();
        clinician.setId(2L);
        clinician.setName(RoleType.CLINICIAN);

        List<RoleDto> dtos = RoleMapper.toDtos(List.of(admin, clinician));

        assertEquals(2, dtos.size());
        assertEquals("ADMIN", dtos.get(0).getName());
        assertEquals("CLINICIAN", dtos.get(1).getName());
    }

    // --- toEntity ---

    @Test
    void toEntity_nullDto_returnsNull() {
        assertNull(RoleMapper.toEntity(null));
    }

    @Test
    void toEntity_validDto_mapsCorrectly() {
        RoleDto dto = new RoleDto();
        dto.setId(1L);
        dto.setName("ADMIN");

        Role role = RoleMapper.toEntity(dto);

        assertEquals(1L, role.getId());
        assertEquals(RoleType.ADMIN, role.getName());
    }

    @Test
    void toEntity_invalidRoleName_throwsException() {
        RoleDto dto = new RoleDto();
        dto.setId(1L);
        dto.setName("INVALID_ROLE");

        assertThrows(IllegalArgumentException.class, () -> RoleMapper.toEntity(dto));
    }

    @Test
    void toEntity_allRoleTypes_mapCorrectly() {
        for (RoleType type : RoleType.values()) {
            RoleDto dto = new RoleDto();
            dto.setId(1L);
            dto.setName(type.name());

            Role role = RoleMapper.toEntity(dto);

            assertEquals(type, role.getName());
        }
    }
}