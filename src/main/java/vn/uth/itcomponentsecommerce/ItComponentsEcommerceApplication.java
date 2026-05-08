package vn.uth.itcomponentsecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ItComponentsEcommerceApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(ItComponentsEcommerceApplication.class, args);
    }

    /**
     * Đọc .env ở project root rồi đẩy vào System properties TRƯỚC khi Spring khởi động.
     * Spring đọc System properties với độ ưu tiên cao hơn application.yaml,
     * nên giá trị trong .env sẽ override default ${VAR:fallback} trong yaml.
     *
     * <p>Phải làm bằng tay vì:
     * <ul>
     *   <li>spring-dotenv 4.0.0 đăng ký qua spring.factories cũ → Spring Boot 4 không load.
     *   <li>Custom EnvironmentPostProcessor (đã thử) cũng không được auto-discover trong setup này.
     * </ul>
     * Đây là cách đơn giản và chắc chắn nhất.
     */
    private static void loadDotenv() {
        Path envFile = Paths.get(System.getProperty("user.dir"), ".env");
        if (!Files.exists(envFile)) {
            System.out.println("[dotenv] No .env file found at " + envFile);
            return;
        }
        int loaded = 0;
        try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (key.isEmpty()) continue;
                // KHÔNG override nếu đã có sẵn trong System properties hoặc env vars OS — giữ ưu tiên cho CI/CD
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                    loaded++;
                }
            }
        } catch (Exception ex) {
            System.err.println("[dotenv] Failed to read " + envFile + ": " + ex.getMessage());
            return;
        }
        System.out.println("[dotenv] Loaded " + loaded + " keys from " + envFile + " into System properties");
    }

}
