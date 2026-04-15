package org.systemdesign.carrentalsystem.service;

import org.systemdesign.carrentalsystem.model.Store;
import org.systemdesign.carrentalsystem.model.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StoreService {
    private final List<Store> stores;

    public StoreService() {
        this.stores = new ArrayList<>();
    }

    public Store addStore(String name, String address, String city) {
        Store store = new Store(name, address, city);
        stores.add(store);
        return store;
    }

    public void addVehicleToStore(Store store, Vehicle vehicle) {
        store.addVehicle(vehicle);
    }

    public boolean removeVehicleFromStore(Store store, Vehicle vehicle) {
        return store.removeVehicle(vehicle);
    }

    public Optional<Store> getStoreById(String storeId) {
        return stores.stream()
                .filter(s -> s.getId().equals(storeId))
                .findFirst();
    }

    public List<Store> getStoresByCity(String city) {
        return stores.stream()
                .filter(s -> s.getCity().equalsIgnoreCase(city))
                .toList();
    }

    public List<Store> getAllStores() {
        return new ArrayList<>(stores);
    }
}

