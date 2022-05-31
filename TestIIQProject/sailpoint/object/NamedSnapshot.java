package sailpoint.object;

/**
 * Simple interface for Snapshots used in IdentitySnapshots
 * that have names. This is useful for stripping the names out for 
 * Differencing.
 */
public interface NamedSnapshot {

    /**
     * Get the name of the object snapshotted
     */
    public String getName();
}