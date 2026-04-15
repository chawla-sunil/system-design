package org.systemdesign.carrentalsystem.model;

import org.systemdesign.carrentalsystem.enums.VehicleCategory;

import java.util.ArrayList;
import java.util.List;

public class VehicleType {
    private final VehicleCategory category;
    private final List<String> features;

    public VehicleType(VehicleCategory category) {
        this.category = category;
        this.features = new ArrayList<>();
    }

    public VehicleType(VehicleCategory category, List<String> features) {
        this.category = category;
        this.features = features;
    }

    public VehicleCategory getCategory() { return category; }
    public List<String> getFeatures() { return features; }

    public void addFeature(String feature) {
        this.features.add(feature);
    }

    @Override
    public String toString() {
        return "VehicleType{category=" + category + ", features=" + features + "}";
    }
}

