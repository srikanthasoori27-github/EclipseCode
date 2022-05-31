/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.certification;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.CertificationItem;

/**
 * Model object for holding CertificationItem status counts
 */
public class CertificationItemStatusCount {

    private static final Log log = LogFactory.getLog(CertificationItemStatusCount.class);

    /**
     * Internal map to hold all the statuses
     */
    private Map<String, Map<String, Integer>> counts = new HashMap<String, Map<String, Integer>>();

    /**
     * Checks if name is contained in Enum.
     *
     * @param clazz The Enum class to check
     * @param name  The name value to test
     * @return {Boolean} If Enum contains name.
     */
    private boolean isValidName(Class<? extends Enum> clazz, String name) {
        Object[] arr = clazz.getEnumConstants();
        for (Object e : arr) {
            if (((Enum) e).name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an Integer value to the counts.  Ensures type and status are valid Enums.
     *
     * @param type   CertificationItem.Type
     * @param status AbstractCertificationItem.Status
     * @param value  Integer value to set
     */
    public void addStatusCount(String type, String status, Integer value) {
        if (!isValidName(CertificationItem.Type.class, type)) {
            log.error("Invalid CertificationItem.Type: " + type);
            return;
        }

        if (!isValidName(AbstractCertificationItem.Status.class, status)) {
            log.error("Invalid AbstractCertificationItem.Status: " + status);
            return;
        }

        if (counts.containsKey(type)) {
            counts.get(type).put(status, value);
        }
        else {
            Map<String, Integer> map = new HashMap<String, Integer>();
            map.put(status, value);
            counts.put(type, map);
        }
    }

    /**
     * Gets a specific Integer value from the counts.
     *
     * @param type   CertificationItem.Type
     * @param status AbstractCertificationItem.Status
     * @return
     */
    public Integer getStatusCount(String type, String status) {
        if (counts.containsKey(type)) {
            return counts.get(type).get(status);
        }
        return null;
    }

    /**
     * Gets the full counts.
     *
     * @return HashMap containing all the status counts.
     */
    public Map getCounts() {
        return counts;
    }

    /**
     * Sets the map
     *
     * @param counts
     */
    public void setCounts(Map<String, Map<String, Integer>> counts) {
        this.counts = counts;
    }
}

