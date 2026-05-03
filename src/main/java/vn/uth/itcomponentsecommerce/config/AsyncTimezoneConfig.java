package vn.uth.itcomponentsecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.TimeZone;
import java.util.concurrent.Executor;

@Configuration
public class AsyncTimezoneConfig {

    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-async-");
        executor.initialize();
        return executor;
    }

    @Bean
    public TimeZone appTimeZone() {
        TimeZone tz = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        TimeZone.setDefault(tz);
        return tz;
    }
}
