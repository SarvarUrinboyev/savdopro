package uz.barakat.market.dto;

import java.util.List;

/**
 * Bulk stock count ("Inventarizatsiya"). For each line the operator enters the
 * physically counted quantity; the service sets the product to that number and
 * logs the difference as a CORRECTION movement.
 */
public record StocktakeRequest(List<StocktakeItem> counts) {

    public record StocktakeItem(Long productId, Integer actual) {}
}
