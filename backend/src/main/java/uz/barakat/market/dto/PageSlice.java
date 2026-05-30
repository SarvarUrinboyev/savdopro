package uz.barakat.market.dto;

import java.util.List;

/**
 * One page of a list endpoint with a forward "load more" cursor.
 *
 * <p>{@code hasMore} is true when at least one more row exists after this
 * page. It is computed without a COUNT query: the repository returns a
 * Spring Data {@code Slice}, which fetches {@code size + 1} rows and reports
 * {@code hasNext()} from whether the extra row was present.
 */
public record PageSlice<T>(List<T> items, boolean hasMore) {
}
