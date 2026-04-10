package com.iam.saml.config;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * OpenSAML 4.0.0 configuration.
 *
 * OpenSAML 4 uses a static initializer — call InitializationService.initialize()
 * at startup to load all registered Initializers (XMLObject providers, etc.).
 */
@Configuration
public class OpenSamlConfig {

    @PostConstruct
    public void bootstrap() {
        try {
            org.opensaml.core.config.InitializationService.initialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bootstrap OpenSAML 4", e);
        }
    }
}
