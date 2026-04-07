package com.green.imagecore.controller;

import com.green.imagecore.config.RequestLogFilter;
import com.green.imagecore.exception.GlobalExceptionHandler;
import com.green.imagecore.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import({GlobalExceptionHandler.class, TestSecurityConfig.class, RequestLogFilter.class})
public abstract class BaseControllerTest {

    @MockitoBean
    protected LogService logService;

    @Autowired
    protected MockMvc mockMvc;
}