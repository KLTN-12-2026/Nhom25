package org.example.meetingbe.config;

import org.example.meetingbe.model.Role;
import org.example.meetingbe.model.User;
import org.example.meetingbe.repository.IRoleRepo;
import org.example.meetingbe.repository.IUserRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RoleInitializer implements ApplicationRunner {
    private final IRoleRepo roleRepo;
    private final IUserRepo userRepo;

    public RoleInitializer(IRoleRepo roleRepo, IUserRepo userRepo) {
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role userRole = ensureRole("USER");
        ensureRole("ADMIN");

        // Repair accounts created before the default roles were initialized.
        for (User user : userRepo.findAll()) {
            if (user.getRoles().isEmpty()) {
                user.getRoles().add(userRole);
                userRepo.save(user);
            }
        }
    }

    private Role ensureRole(String name) {
        return roleRepo.findByRoleName(name).stream().findFirst().orElseGet(() -> {
            Role role = new Role();
            role.setRoleName(name);
            return roleRepo.save(role);
        });
    }
}
