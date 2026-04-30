package org.systemdesign.movieticketbooking.strategy;

import org.systemdesign.movieticketbooking.model.Show;
import org.systemdesign.movieticketbooking.model.ShowSeat;

import java.util.List;

/**
 * Decorator: applies a multiplier on top of the base pricing strategy.
 * Example: 1.5x for weekends, 2.0x for holidays.
 *
 * This is a combination of Strategy + Decorator patterns.
 */
public class PeakHourPricingStrategy implements PricingStrategy {

    private final PricingStrategy baseStrategy;
    private final double multiplier;

    public PeakHourPricingStrategy(PricingStrategy baseStrategy, double multiplier) {
        this.baseStrategy = baseStrategy;
        this.multiplier = multiplier;
    }

    @Override
    public double calculatePrice(Show show, List<ShowSeat> selectedSeats) {
        double basePrice = baseStrategy.calculatePrice(show, selectedSeats);
        return basePrice * multiplier;
    }
}

