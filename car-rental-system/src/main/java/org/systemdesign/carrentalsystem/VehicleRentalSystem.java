package org.systemdesign.carrentalsystem;

import org.systemdesign.carrentalsystem.service.*;

/**
 * Singleton Pattern: VehicleRentalSystem is the single entry point
 * that wires together all services.
 *
 * Why Singleton?
 * - There should be only ONE rental system instance managing the entire fleet.
 * - All stores, users, reservations are managed by this single system.
 * - Provides a centralized access point for all operations.
 */
public class VehicleRentalSystem {

    private static VehicleRentalSystem instance;

    private final UserService userService;
    private final StoreService storeService;
    private final VehicleService vehicleService;
    private final ReservationService reservationService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    private VehicleRentalSystem() {
        this.userService = new UserService();
        this.storeService = new StoreService();
        this.vehicleService = new VehicleService();
        this.invoiceService = new InvoiceService();
        this.reservationService = new ReservationService(vehicleService, invoiceService);
        this.paymentService = new PaymentService();
    }

    public static synchronized VehicleRentalSystem getInstance() {
        if (instance == null) {
            instance = new VehicleRentalSystem();
        }
        return instance;
    }

    // For testing — allows resetting the singleton
    public static synchronized void resetInstance() {
        instance = null;
    }

    public UserService getUserService() { return userService; }
    public StoreService getStoreService() { return storeService; }
    public VehicleService getVehicleService() { return vehicleService; }
    public ReservationService getReservationService() { return reservationService; }
    public InvoiceService getInvoiceService() { return invoiceService; }
    public PaymentService getPaymentService() { return paymentService; }
}

