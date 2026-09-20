package com.paymentgateway.engine.domain.policy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockOrderPolicyTest {

    private final LockOrderPolicy policy = new LockOrderPolicy();

    @Test
    void resolveLockOrder_whenSourceIsSmaller_returnsSourceFirst() {
        UUID smaller = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID larger = UUID.fromString("00000000-0000-0000-0000-000000000002");

        LockOrder order = policy.resolveLockOrder(smaller, larger);

        assertThat(order.first()).isEqualTo(smaller);
        assertThat(order.second()).isEqualTo(larger);
    }

    @Test
    void resolveLockOrder_whenTargetIsSmaller_returnsTargetFirst() {
        UUID larger = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID smaller = UUID.fromString("00000000-0000-0000-0000-000000000001");

        // source=larger, target=smaller -- el orden de LLAMADA es indistinto
        // al resultado: siempre gana el menor por UUID.compareTo(), sin
        // importar si era el origen o el destino de la transferencia.
        LockOrder order = policy.resolveLockOrder(larger, smaller);

        assertThat(order.first()).isEqualTo(smaller);
        assertThat(order.second()).isEqualTo(larger);
    }

    @Test
    void resolveLockOrder_isSymmetric_regardlessOfCallDirection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        LockOrder orderAB = policy.resolveLockOrder(a, b);
        LockOrder orderBA = policy.resolveLockOrder(b, a);

        // Invariante central que previene RISK-001: A->B y B->A concurrentes
        // deben producir el MISMO orden de adquisición de locks.
        assertThat(orderAB.first()).isEqualTo(orderBA.first());
        assertThat(orderAB.second()).isEqualTo(orderBA.second());
    }

    @Test
    void resolveLockOrder_withEqualIds_doesNotThrowAndReturnsSameIdTwice() {
        UUID sameId = UUID.randomUUID();

        LockOrder order = policy.resolveLockOrder(sameId, sameId);

        assertThat(order.first()).isEqualTo(sameId);
        assertThat(order.second()).isEqualTo(sameId);
    }

    @Test
    void resolveLockOrder_withNullSourceId_throwsNullPointerException() {
        assertThatThrownBy(() -> policy.resolveLockOrder(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolveLockOrder_withNullTargetId_throwsNullPointerException() {
        assertThatThrownBy(() -> policy.resolveLockOrder(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }
}