package com.example.channelbot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@PropertySource("application.properties")
public class Config {
    @Value("${bot.name}")
    String botName;
    @Value("${bot.token}")
    String botToken;
}
