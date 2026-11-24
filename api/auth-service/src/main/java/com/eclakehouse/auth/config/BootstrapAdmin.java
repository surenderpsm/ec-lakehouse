package com.eclakehouse.auth.config;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.eclakehouse.auth.user.Role;
import com.eclakehouse.auth.user.User;
import com.eclakehouse.auth.user.UserRepository;

/** Creates the first admin from ec.auth.bootstrap-admin.* so someone can approve vendors. */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AuthProperties props;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    BootstrapAdmin(AuthProperties props, UserRepository users, PasswordEncoder passwordEncoder) {
        this.props = props;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = props.bootstrapAdmin();
        if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            return;
        }
        if (users.existsByEmailIgnoreCase(admin.email())) {
            return;
        }
        users.save(new User(admin.email(), passwordEncoder.encode(admin.password()), "Administrator",
                EnumSet.of(Role.ADMIN, Role.CUSTOMER)));
        log.info("Created bootstrap admin {}", admin.email());
    }
}
