package world.sirius.ops.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerConfig {

    /** Подключение к Docker Engine API. Реальное соединение открывается только при первом вызове, а не при старте. */
    @Bean(destroyMethod = "close")
    DockerClient dockerClient(@Value("${sirius.docker.host:unix:///var/run/docker.sock}") String host) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)                          // unix-сокет или tcp://docker-proxy:2375
                .build();
        ApacheDockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(20)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        return DockerClientImpl.getInstance(config, http);
    }
}
