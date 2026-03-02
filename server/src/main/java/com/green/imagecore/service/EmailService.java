package com.green.imagecore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        System.out.println("----------------------------");
        System.out.println(resetLink);
        System.out.println("----------------------------");
    }
}
