package org.example.capstone_groupproject_csc311.backend;

import org.example.capstone_groupproject_csc311.UserProfile.ProfileRepository;
import org.example.capstone_groupproject_csc311.UserProfile.User;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        scanBasePackages = "org.example.capstone_groupproject_csc311",
        proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
@EntityScan(basePackageClasses = User.class)
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
