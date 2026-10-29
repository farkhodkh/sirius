package world.sirius.ops.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Frame;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


@Service
public class ContainerService {

    private static final String PROJECT_LABEL = "com.docker.compose.project";           // метку ставит Docker Compose
    private static final Pattern CONTAINER_ID = Pattern.compile("[0-9a-f]{12,64}");     // только идентификатор, не имя и не произвольная строка

    private final DockerClient docker;
    private final String project;

    public ContainerService(DockerClient docker, @Value("${sirius.docker.project:sirius}") String project) {
        this.docker = docker;
        this.project = project;
    }

    public List<ContainerView> list() {
        return callDocker(() -> docker.listContainersCmd()
                .withShowAll(true)                                              // и остановленные
                .withLabelFilter(Map.of(PROJECT_LABEL, project))                // только контейнеры нашего проекта
                .exec().stream()
                .map(c -> new ContainerView(c.getId().substring(0, 12), String.join(",", c.getNames()),
                        c.getImage(), c.getState(), c.getStatus()))
                .toList());
    }

    public void restart(String id) {
        requireOurs(id);
        callDocker(() -> {
            docker.restartContainerCmd(id).withTimeout(10).exec();              // 10 с на мягкую остановку, затем принудительно
            return null;
        });
    }

    public String tailLogs(String id, int lines) throws InterruptedException {
        requireOurs(id);
        StringBuilder out = new StringBuilder();
        ResultCallback.Adapter<Frame> callback = callDocker(() -> docker.logContainerCmd(id)
                .withStdOut(true).withStdErr(true).withTail(lines)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        out.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                    }
                }));
        callback.awaitCompletion(5, TimeUnit.SECONDS);
        return out.toString();
    }

    /** Трогать можно только контейнеры нашего проекта: идентификатор проверяется до любого обращения к Docker. */
    private void requireOurs(String id) {
        if (!CONTAINER_ID.matcher(id).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный идентификатор контейнера");
        }
        Map<String, String> labels = callDocker(() -> docker.inspectContainerCmd(id).exec().getConfig().getLabels());
        if (labels == null || !project.equals(labels.get(PROJECT_LABEL))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Контейнер не принадлежит проекту");
        }
    }

    /**
     * Любой вызов Docker выполняется здесь: ошибки Docker превращаются в понятные коды ответа вместо «500»:
     * 404 - нет такого контейнера; 502 - Docker (или docker-proxy) отказал или недоступен.
     */
    private <T> T callDocker(Supplier<T> call) {
        try {
            return call.get();
        } catch (DockerException e) {
            int status = e.getHttpStatus();
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Контейнер не найден");
            }
            if (status == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Docker API отклонил операцию (403): проверьте, разрешена ли она в docker-proxy", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка Docker API (код " + status + ")", e);
        } catch (DockerClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось выполнить запрос к Docker API", e);
        }
    }
}