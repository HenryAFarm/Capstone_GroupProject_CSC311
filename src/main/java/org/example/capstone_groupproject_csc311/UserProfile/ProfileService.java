package org.example.capstone_groupproject_csc311.UserProfile;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ProfileService {
    private final ProfileRepository repository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(ProfileRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean register(String username, String rawPassword) {
        if (repository.existsByUsername(username)) {
            return false;
        }

        String hashedPassword = passwordEncoder.encode(rawPassword);
        repository.save(new User(username, hashedPassword));
        return true;
    }

    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username);
    }
}
