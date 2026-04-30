package org.systemdesign.movieticketbooking.strategy;

import org.systemdesign.movieticketbooking.model.Show;
import org.systemdesign.movieticketbooking.model.ShowSeat;

import java.util.List;

/**
 * Strategy interface for calculating ticket prices.
 * Different implementations for different pricing models (weekday, weekend, holiday, etc.)
 */
public interface PricingStrategy {
    double calculatePrice(Show show, List<ShowSeat> selectedSeats);
}

