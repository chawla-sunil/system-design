package org.systemdesign.pg.repository;

import org.systemdesign.pg.enums.PaymentStatus;
import org.systemdesign.pg.model.Payment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory payment repository.
 *
 * In real world: backed by a database (PostgreSQL/MySQL) with proper transactions.
 * Uses ConcurrentHashMap for thread safety.
 */
public class PaymentRepository {

    // paymentId → Payment
    private final Map<String, Payment> paymentStore = new ConcurrentHashMap<>();

    // orderId → List<paymentId> (one order can have retry attempts)
    private final Map<String, List<String>> orderPaymentIndex = new ConcurrentHashMap<>();

    public void save(Payment payment) {
        paymentStore.put(payment.getPaymentId(), payment);
        orderPaymentIndex
                .computeIfAbsent(payment.getOrderId(), k -> new ArrayList<>())
                .add(payment.getPaymentId());
    }

    public Optional<Payment> findById(String paymentId) {
        return Optional.ofNullable(paymentStore.get(paymentId));
    }

    public List<Payment> findByOrderId(String orderId) {
        List<String> paymentIds = orderPaymentIndex.getOrDefault(orderId, Collections.emptyList());
        return paymentIds.stream()
                .map(paymentStore::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Payment> findByUserId(String userId) {
        return paymentStore.values().stream()
                .filter(p -> p.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public List<Payment> findByStatus(PaymentStatus status) {
        return paymentStore.values().stream()
                .filter(p -> p.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Collection<Payment> findAll() {
        return Collections.unmodifiableCollection(paymentStore.values());
    }
}

