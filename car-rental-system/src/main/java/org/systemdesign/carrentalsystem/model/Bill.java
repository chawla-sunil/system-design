package org.systemdesign.carrentalsystem.model;

public class Bill {
    private final double baseCost;
    private final double taxRate;
    private final double taxAmount;
    private final double discount;
    private final double totalAmount;

    public Bill(double baseCost, double taxRate, double discount) {
        this.baseCost = baseCost;
        this.taxRate = taxRate;
        this.taxAmount = baseCost * taxRate;
        this.discount = discount;
        this.totalAmount = baseCost + this.taxAmount - discount;
    }

    public double getBaseCost() { return baseCost; }
    public double getTaxRate() { return taxRate; }
    public double getTaxAmount() { return taxAmount; }
    public double getDiscount() { return discount; }
    public double getTotalAmount() { return totalAmount; }

    @Override
    public String toString() {
        return String.format("Bill{base=₹%.2f, tax=₹%.2f (%.0f%%), discount=₹%.2f, total=₹%.2f}",
                baseCost, taxAmount, taxRate * 100, discount, totalAmount);
    }
}

