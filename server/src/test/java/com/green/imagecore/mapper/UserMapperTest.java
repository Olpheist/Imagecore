package com.green.imagecore.mapper;

import com.green.imagecore.dto.UserDto;
import com.green.imagecore.entities.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    @Test
    void toDto_ShouldMapFieldsAndExcludePassword() {
        User entity = new User();
        entity.setId(1L);
        entity.setUsername("tester");
        entity.setEmail("test@test.com");
        entity.setPasswordHash("SECRET_HASH");

        UserDto dto = UserMapper.toDto(entity);

        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getUsername(), dto.getUsername());
        // Implicitly verify that password is not leaked because UserDto lacks the field
        assertNotNull(dto);
    }
}