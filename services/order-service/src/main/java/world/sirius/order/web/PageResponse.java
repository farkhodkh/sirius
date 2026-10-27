package world.sirius.order.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Собственный формат страницы: стабильный JSON-контракт, не зависящий от внутренностей Spring Data. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}