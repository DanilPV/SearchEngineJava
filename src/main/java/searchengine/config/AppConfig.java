package searchengine.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
public class AppConfig {

    @Value("${app.userAgent}")
    private String userAgent;

    @Value("${app.referrer}")
    private String referrer;

}
