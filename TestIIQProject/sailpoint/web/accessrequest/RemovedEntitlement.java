/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.accessrequest;

import java.util.Map;

/**
 * This is a pseudo DTO object used to hold Entitlement data for removal requests.
 *
 * @author: michael.hide
 * Created: 10/8/14 10:11 AM
 */
public class RemovedEntitlement extends RemovedAccessItem {

    // Public constants
    public static final String INSTANCE = "instance";
    public static final String NATIVE_IDENTITY = "nativeIdentity";
    public static final String APPLICATION = "application";
    public static final String ATTRIBUTE = "attribute";
    public static final String VALUE = "value";

    // Member variables
    private String instance;
    private String nativeIdentity;
    private String application;
    private String attribute;
    private String value;

    /**
     * Takes configs in constructor and sets member variables
     *
     * @param data Map of Entitlement configs
     */
    public RemovedEntitlement(Map<String, Object> data) {
        super(data);

        if (data != null) {
            instance = (String)data.get(INSTANCE);
            nativeIdentity = (String)data.get(NATIVE_IDENTITY);
            application = (String)data.get(APPLICATION);
            attribute = (String)data.get(ATTRIBUTE);
            value = (String)data.get(VALUE);
        }
    }

    /**
     * Returns the instance name
     *
     * @return String
     */
    public String getInstance() {
        return this.instance != null ? this.instance : "";
    }

    /**
     * Returns the native identity
     *
     * @return String
     */
    public String getNativeIdentity() {
        return this.nativeIdentity != null ? this.nativeIdentity : "";
    }

    /**
     * Returns the application
     * @return String
     */
    public String getApplication() {
        return application;
    }

    /**
     * Returns the attribute
     * @return String
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * Returns the value
     * @return String
     */
    public String getValue() {
        return value;
    }


}
