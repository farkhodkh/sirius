package world.sirius.ops.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import world.sirius.ops.service.ContainerService;
import world.sirius.ops.service.ContainerView;

@RestController
@RequestMapping("/api/v1/ops/containers")
@PreAuthorize("hasRole('ADMIN')")                          // только администраторы (claim roles -> ROLE_ADMIN)
public class OpsController {

    private static final int MAX_LINES = 1000;

    private final ContainerService containers;

    public OpsController(ContainerService containers) {
        this.containers = containers;
    }

    @GetMapping
    public List<ContainerView> list() {
        return containers.list();
    }

    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void restart(@PathVariable String id) {
        containers.restart(id);
    }

    @GetMapping(value = "/{id}/logs", produces = "text/plain;charset=UTF-8")
    public String logs(@PathVariable String id, @RequestParam(defaultValue = "100") int lines) throws InterruptedException {
        return containers.tailLogs(id, Math.min(Math.max(lines, 1), MAX_LINES));
    }
}
