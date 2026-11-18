package nhh.reminder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableScheduling
public class AppConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(name = "mailQueueExecutor")
    ThreadPoolTaskExecutor mailQueueExecutor(@Value("${app.mail.worker-count:3}") int workerCount) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("app.mail.worker-count phải lớn hơn 0.");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(workerCount);
        executor.setThreadNamePrefix("mail-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
