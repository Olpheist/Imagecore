package com.green.imagecore.service;

import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.RoleType;
import com.green.imagecore.repositories.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void findAll_ReturnsAllRoles() {
        Role admin = new Role();
        admin.setId(1L);
        admin.setName(RoleType.ADMIN);

        Role clinician = new Role();
        clinician.setId(2L);
        clinician.setName(RoleType.CLINICIAN);

        when(roleRepository.findAll()).thenReturn(List.of(admin, clinician));

        List<Role> result = roleService.findAll();

        assertEquals(2, result.size());
        assertEquals(RoleType.ADMIN, result.get(0).getName());
        assertEquals(RoleType.CLINICIAN, result.get(1).getName());
        verify(roleRepository).findAll();
    }

    @Test
    void findAll_ReturnsEmptyList_WhenNoRoles() {
        when(roleRepository.findAll()).thenReturn(List.of());

        List<Role> result = roleService.findAll();

        assertTrue(result.isEmpty());
        verify(roleRepository).findAll();
    }
}