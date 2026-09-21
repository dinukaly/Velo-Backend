package com.dinukaly.velo.util;

import java.util.HashSet;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serializes local creation/reconciliation and protects uncommitted session creation. */
public final class SandboxLifecycle {
    public static final Object MONITOR = new Object();
    public static final String DEPLOYMENT_LABEL = "com.velo.sandbox.deployment";
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private SandboxLifecycle() {}

    public static void protectUntilCommit(String id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        synchronized (MONITOR) { IN_FLIGHT.add(id); }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                synchronized (MONITOR) { IN_FLIGHT.remove(id); }
            }
        });
    }

    public static boolean isInFlight(String id) {
        synchronized (MONITOR) { return IN_FLIGHT.contains(id); }
    }
}
