/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.tools.Util;

/**
 * This class contains all of the information needed to retrieve a recommendation.  This will include the
 * recommendation request type, and any parameters needed to get that type of recommendation.
 */
public class RecommendationRequest {

    /**
     * Request type.  Lets the recommender know what type of recommendation is being requested.
     *
     * IDENTITY_ENTITLEMENT - required attributes are IDENTITY_ID and ENTITLEMENT_ID.
     */
    public enum RequestType {
        UNKNOWN,
        IDENTITY_ENTITLEMENT_ADD,
        IDENTITY_ENTITLEMENT_REMOVE,
        IDENTITY_ROLE_ADD,
        IDENTITY_ROLE_REMOVE,
        IDENTITY_PERMISSION
    }

    public static final String ENTITLEMENT_ID = "entitlementId";
    public static final String IDENTITY_ID = "identityId";
    public static final String ROLE_ID = "roleId";

    Map<String, Object> attributes = new HashMap<String, Object>();
    RequestType requestType = RequestType.UNKNOWN;

    /**
     * Gets the attributes for this request.  Each request type may have different required attributes.
     * @return the map of attributes.
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Sets the attributes for this request.  Each request type may have different required attributes.
     *
     * @param attributes
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    @JsonIgnore
    public <T> T getAttribute(String attributeName, Class<T> type) {
        return (T) attributes.get(attributeName);
    }

    @JsonIgnore
    public void setAttribute(String attributeName, Object value) {
        attributes.put(attributeName, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return hashCode() == o.hashCode();
    }

    @Override
    public int hashCode() {

        return Objects.hash(getRequestIds(), requestType);
    }

    /**
     * Generates a single string with all IDs in this recommendation request concatenated together.
     * Used for uniqueness.
     *
     * @return Unique string generated from the IDs in this request.
     */
    private String getRequestIds() {
        String result = getAttribute(IDENTITY_ID, String.class);

        switch (getRequestType()) {
            case IDENTITY_ENTITLEMENT_ADD:
            case IDENTITY_ENTITLEMENT_REMOVE:
                result += getAttribute(ENTITLEMENT_ID, String.class);
                break;
            case IDENTITY_ROLE_ADD:
            case IDENTITY_ROLE_REMOVE:
                result += getAttribute(ROLE_ID, String.class);
                break;
            default:
                result += Util.itoa(attributes.hashCode());
        }

        return result;
    }
}
