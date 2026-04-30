package org.systemdesign.movieticketbooking.strategy;

import org.systemdesign.movieticketbooking.model.Show;
import org.systemdesign.movieticketbooking.model.ShowSeat;
import org.systemdesign.movieticketbooking.model.enums.SeatType;

import java.util.List;
import java.util.Map;

/**
 * Base pricing: fixed price per seat type.
 *   SILVER   → ₹150
 *   GOLD     → ₹250
 *   PLATINUM → ₹400
 */
public class BasePricingStrategy implements PricingStrategy {

    private final Map<SeatType, Double> priceMap;

    public BasePricingStrategy() {
        this.priceMap = Map.of(
                SeatType.SILVER, 150.0,
                SeatType.GOLD, 250.0,
                SeatType.PLATINUM, 400.0
        );
    }

    public BasePricingStrategy(Map<SeatType, Double> customPriceMap) {
        this.priceMap = customPriceMap;
    }

    @Override
    public double calculatePrice(Show show, List<ShowSeat> selectedSeats) {
        return selectedSeats.stream()
                .mapToDouble(ss -> priceMap.getOrDefault(ss.getSeat().getSeatType(), 150.0))
                .sum();
    }
}

