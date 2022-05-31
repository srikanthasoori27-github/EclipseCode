/* (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Identity;

/**
 * This DTO contains a list of identity attributes to display for a user.  Each attribute has a localized
 * label, a value (which may be an IdentitySummary for an identity-type attribute), and whether - if an identity -
 * the logged in user is authorized to view the details of the referenced identity.
 */
public class IdentityAttributesDTO {

    /**
     * A single identity attribute to display.
     */
    public static class IdentityAttributeDTO {

        private String attributeName;
        private String label;
        private Object value;
        private boolean authorizedToView;

        /**
         * Constructor.
         *
         * @param attrName  The name of the identity attribute.
         * @param label     The localized label to display for the attribute.
         * @param value     The value to display - either a primitive or an IdentitySummary if the attribute
         *                  references an identity.
         */
        private IdentityAttributeDTO(String attrName, String label, Object value) {
            this.attributeName = attrName;
            this.label = label;
            this.value = value;
            this.authorizedToView = true;
        }

        public String getAttributeName() {
            return this.attributeName;
        }

        public String getLabel() {
            return this.label;
        }

        public Object getValue() {
            return this.value;
        }

        public boolean isAuthorizedToView() {
            return this.authorizedToView;
        }

        /**
         * Set to false if the logged in user is not authorized to view the details of the referenced
         * identity - if this attribute references an identity.  By default this is true.
         *
         * @param authorizedToView Whether the user is authorized to view the details of the referenced identity.
         */
        public void setAuthorizedToView(boolean authorizedToView) {
            this.authorizedToView = authorizedToView;
        }
    }


    private List<IdentityAttributeDTO> attributes;
    
    /**
     * Constructor.
     */
    public IdentityAttributesDTO() {
        this.attributes = new ArrayList<IdentityAttributeDTO>();
    }

    /**
     * Add the given attribute - if the value is an Identity, it will be converted to an IdentitySummaryDTO.
     *
     * @param attrName  The name of the identity attribute.
     * @param label     The localized label to display for the attribute.
     * @param value     The value to display - either a primitive or an IdentitySummary if the attribute
     *                  references an identity.
     */
    public void add(String attrName, String label, Object value) {
        if (value instanceof Identity) {
            value = new IdentitySummaryDTO((Identity) value);
        }
        this.attributes.add(new IdentityAttributeDTO(attrName, label, value));
    }

    /**
     * Return a non-null list of the identity attributes.
     */
    public List<IdentityAttributeDTO> getAttributes() {
        return attributes;
    }
}

