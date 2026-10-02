package dev.leopaul.colonyledger.service;

/** Both removals precede either assignment so full houses can be swapped without exceeding capacity.
 * Execute synchronously on the server thread, after revalidation. Rolls back a failed assignment. */
public final class HousingAssignmentService {
    public enum Result { SUCCESS, ROLLED_BACK, ROLLBACK_FAILED }
    public interface Assignments<C, H> {
        boolean remove(H home, C citizen);
        boolean assign(H home, C citizen);
        boolean contains(H home, C citizen);
    }
    private HousingAssignmentService() {}
    public static <C, H> Result swap(Assignments<C, H> port, C a, C b, H ah, H bh) {
        try {
            if (!port.remove(ah, a) || !port.remove(bh, b)) throw new IllegalStateException("Removal rejected");
            if (!port.assign(bh, a) || !port.assign(ah, b)) throw new IllegalStateException("Assignment rejected");
            if (!port.contains(bh, a) || !port.contains(ah, b)) throw new IllegalStateException("Assignment inconsistent");
            return Result.SUCCESS;
        } catch (RuntimeException failure) {
            try {
                if (port.contains(bh, a)) port.remove(bh, a);
                if (port.contains(ah, b)) port.remove(ah, b);
                if (!port.contains(ah, a)) port.assign(ah, a);
                if (!port.contains(bh, b)) port.assign(bh, b);
                return port.contains(ah, a) && port.contains(bh, b) ? Result.ROLLED_BACK : Result.ROLLBACK_FAILED;
            } catch (RuntimeException rollbackFailure) { return Result.ROLLBACK_FAILED; }
        }
    }
}
