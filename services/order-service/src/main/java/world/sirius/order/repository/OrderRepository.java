package world.sirius.order.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import world.sirius.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByCustomerIdAndIdempotencyKey(UUID customerId, String key);

    // Страница заказов клиента. Коллекцию items НЕ подтягиваем здесь (пагинация + fetch join коллекции = загрузка всего в память)
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    // Один заказ с позициями ОДНИМ запросом (JOIN FETCH), для карточки заказа
    @Query("select o from Order o left join fetch o.items where o.id = :id and o.customerId = :cid")
    Optional<Order> findWithItems(@Param("id") UUID id, @Param("cid") UUID customerId);
}