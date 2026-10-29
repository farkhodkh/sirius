package world.sirius.ops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ContainerServiceTest {

    static final String OURS = "0123456789ab";
    static final String FOREIGN = "ba9876543210";

    DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    ContainerService service;

    @BeforeEach
    void setUp() {
        service = new ContainerService(docker, "sirius");
    }

    void containerHasLabels(String id, Map<String, String> labels) {
        when(docker.inspectContainerCmd(id).exec().getConfig().getLabels()).thenReturn(labels);
    }

    @Test
    void listAsksDockerOnlyForProjectContainersAndShortensIds() {
        Container c = mock(Container.class);
        when(c.getId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(c.getNames()).thenReturn(new String[] {"/sirius-postgres"});
        when(c.getImage()).thenReturn("postgres:17");
        when(c.getState()).thenReturn("running");
        when(c.getStatus()).thenReturn("Up 8 hours (healthy)");
        // заглушка срабатывает ТОЛЬКО при фильтре по метке нашего проекта; иначе Docker «вернул бы» пустой список
        when(docker.listContainersCmd().withShowAll(true)
                .withLabelFilter(eq(Map.of("com.docker.compose.project", "sirius"))).exec()).thenReturn(List.of(c));

        List<ContainerView> views = service.list();

        assertThat(views).containsExactly(new ContainerView("0123456789ab", "/sirius-postgres", "postgres:17",
                "running", "Up 8 hours (healthy)"));
    }

    @Test
    void restartsOwnContainer() {
        containerHasLabels(OURS, Map.of("com.docker.compose.project", "sirius"));

        service.restart(OURS);

        verify(docker.restartContainerCmd(OURS).withTimeout(10)).exec();
    }

    @Test
    void refusesToRestartForeignContainer() {
        containerHasLabels(FOREIGN, Map.of("com.docker.compose.project", "other-project"));

        assertThatThrownBy(() -> service.restart(FOREIGN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(docker, never()).restartContainerCmd(any());
    }

    @Test
    void containerWithoutLabelsIsForeign() {
        containerHasLabels(FOREIGN, null);

        assertThatThrownBy(() -> service.restart(FOREIGN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void malformedIdIsRejectedBeforeAnyDockerCall() {
        assertThatThrownBy(() -> service.restart("../../etc/passwd"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.tailLogs("sirius-postgres", 10))        // имя, а не идентификатор
                .isInstanceOf(ResponseStatusException.class);
        verify(docker, never()).inspectContainerCmd(any());
    }

    @Test
    void unknownContainerIsNotFound() {
        when(docker.inspectContainerCmd(OURS).exec()).thenThrow(new NotFoundException("no such container"));

        assertThatThrownBy(() -> service.restart(OURS))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void tailLogsCollectsFramesIntoText() throws Exception {
        containerHasLabels(OURS, Map.of("com.docker.compose.project", "sirius"));
        when(docker.logContainerCmd(OURS).withStdOut(true).withStdErr(true).withTail(2).exec(any()))
                .thenAnswer(inv -> {
                    ResultCallback.Adapter<Frame> callback = inv.getArgument(0);
                    callback.onNext(new Frame(StreamType.STDOUT, "первая строка\n".getBytes()));
                    callback.onNext(new Frame(StreamType.STDOUT, "вторая строка\n".getBytes()));
                    callback.onComplete();
                    return callback;
                });

        assertThat(service.tailLogs(OURS, 2)).isEqualTo("первая строка\nвторая строка\n");
    }
}