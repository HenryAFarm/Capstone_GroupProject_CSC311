package org.example.capstone_groupproject_csc311.UserProfile;

import org.example.capstone_groupproject_csc311.UserProfile.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final ProfileService profileService;

    public DatabaseUserDetailsService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User appUser = profileService.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(appUser.getUsername())
                .password(appUser.getPassword())
                .roles("USER")
                .build();
    }
}
