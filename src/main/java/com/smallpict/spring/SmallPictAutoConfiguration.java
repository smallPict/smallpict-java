package com.smallpict.spring;

import com.smallpict.SmallPictClient;
import com.smallpict.SmallPictConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(SmallPictClient.class)
@EnableConfigurationProperties(SmallPictProperties.class)
public class SmallPictAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SmallPictClient smallPictClient(SmallPictProperties properties) {
        SmallPictConfig config = SmallPictConfig.builder()
                .apiKey(properties.getApiKey())
                .secretKey(properties.getSecretKey())
                .baseUrl(properties.getBaseUrl())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .fallbackMode(properties.getFallbackMode())
                .pollInterval(properties.getPollInterval())
                .waitTimeout(properties.getWaitTimeout())
                .build();
        return new SmallPictClient(config);
    }
}
