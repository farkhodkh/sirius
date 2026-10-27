package world.sirius.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.sirius.order.domain.Order;
import world.sirius.order.services.CreateOrderCommand;
import world.sirius.order.services.OrderNotFoundException;
import world.sirius.order.services.OrderReadService;
import world.sirius.order.services.OrderService;


@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService service;
    private final OrderReadService reads;

    public OrderController(OrderService service, OrderReadService reads) {
        this.service = service;
        this.reads = reads;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_orders.write')")        // нужен scope orders.write в токене
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal Jwt jwt,                  // пользователь берётся из проверенного токена, НЕ из тела запроса
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String idemKey,
            @Valid @RequestBody CreateOrderRequest req) {

        UUID customerId = UUID.fromString(jwt.getSubject());   // sub = id пользователя
        String email = jwt.getClaimAsString("email");
        Order order = service.create(customerId, email, CreateOrderCommand.from(req), idemKey);

        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + order.getId()))   // 201 + Location
                .body(OrderResponse.from(order));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_orders.read')")
    public PageResponse<OrderSummary> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);   // клиент не может запросить size=1000000
        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(service.list(UUID.fromString(jwt.getSubject()), pageable), OrderSummary::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_orders.read')")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) throws OrderNotFoundException {
        // фильтр по customerId внутри запроса = защита от IDOR (чужой заказ по угаданному id)
        OrderResponse found = reads.find(id, UUID.fromString(jwt.getSubject()));   // с кэшем Redis (5 минут)
        if (found == null) {
            throw new OrderNotFoundException(id);
        }
        return found;
    }
}
