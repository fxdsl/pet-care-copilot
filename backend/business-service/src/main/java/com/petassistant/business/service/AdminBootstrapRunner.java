package com.petassistant.business.service;

import com.petassistant.business.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 根据显式环境变量创建首次本地管理员，不提供默认管理员密码。 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final SecurityProperties properties;
    private final AuthService authService;

    public AdminBootstrapRunner(SecurityProperties properties, AuthService authService) {
        this.properties = properties;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getBootstrapAdminUsername().isBlank()
                || properties.getBootstrapAdminPassword().isBlank()) {
            return;
        }
        if (authService.bootstrapAdmin(
                properties.getBootstrapAdminUsername(),
                properties.getBootstrapAdminPassword()
        )) {
            log.info("Configured bootstrap administrator was created");
        } else {
            log.info("Bootstrap administrator was not created because the username already exists or is invalid");
        }
    }
}
