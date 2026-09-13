package com.rrsistemas.erauma;

import com.rrsistemas.erauma.auth.ResendEmailProperties;
import com.rrsistemas.erauma.config.AuthRateLimitProperties;
import com.rrsistemas.erauma.story.OpenAiProperties;
import com.rrsistemas.erauma.story.OpenAiImageProperties;
import com.rrsistemas.erauma.story.StoryAiProperties;
import com.rrsistemas.erauma.story.StoryImageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({OpenAiProperties.class, OpenAiImageProperties.class, StoryAiProperties.class, StoryImageProperties.class, ResendEmailProperties.class, AuthRateLimitProperties.class})
@EnableScheduling
public class EraUmaApplication {
    public static void main(String[] args) {
        SpringApplication.run(EraUmaApplication.class, args);
    }
}
