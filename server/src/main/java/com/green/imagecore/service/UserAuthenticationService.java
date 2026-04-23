package com.green.imagecore.service;

import com.green.imagecore.entities.User;
import com.green.imagecore.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAuthenticationService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return toUserDetails(user);
    }

    public UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .disabled(!user.isEnabled())
                .authorities(
                        user.getUserRoles()
                                .stream()
                                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getName().name()))
                                .toList()
                )
                .build();
    }
}
