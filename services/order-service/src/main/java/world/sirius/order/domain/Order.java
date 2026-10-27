package world.sirius.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;                                   // генерируем в приложении (UUID) — не нужен round-trip к sequence

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)                       // STRING, а не ORDINAL: порядок enum в коде не должен ломать данные
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)     
    private String currency;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Version                                           // оптимистичная блокировка: UPDATE ... WHERE version = ? ; при конфликте — OptimisticLockException
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // LAZY по умолчанию для коллекций; cascade ALL + orphanRemoval: позиции живут и умирают вместе с заказом
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}                               // конструктор для Hibernate

    public static Order create(UUID customerId, String email, String currency, String idemKey,
                               List<OrderItem> items) {
        Order o = new Order();
        o.id = UUID.randomUUID();
        o.customerId = customerId;
        o.customerEmail = email;
        o.currency = currency;
        o.idempotencyKey = idemKey;
        o.status = OrderStatus.CREATED;
        items.forEach(o::addItem);
        o.totalAmount = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return o;
    }

    private void addItem(OrderItem item) { item.setOrder(this); items.add(item); }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
    
    
    // геттеры опущены для краткости: id, customerId, customerEmail, status, totalAmount, currency, items, createdAt
}
