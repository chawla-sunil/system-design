package org.systemdesign.pg.repository;

import org.systemdesign.pg.model.Refund;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory refund repository.
 */
public class RefundRepository {

    private final Map<String, Refund> refundStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> paymentRefundIndex = new ConcurrentHashMap<>();

    public void save(Refund refund) {
        refundStore.put(refund.getRefundId(), refund);
        paymentRefundIndex
                .computeIfAbsent(refund.getPaymentId(), k -> new ArrayList<>())
                .add(refund.getRefundId());
    }

    public Optional<Refund> findById(String refundId) {
        return Optional.ofNullable(refundStore.get(refundId));
    }

    public List<Refund> findByPaymentId(String paymentId) {
        List<String> refundIds = paymentRefundIndex.getOrDefault(paymentId, Collections.emptyList());
        return refundIds.stream()
                .map(refundStore::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}

