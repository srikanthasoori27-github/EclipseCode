/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.constraint.impl;

import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.rapidsetup.model.AttributeValueDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class IdentityEvaluator extends BaseAttributeConstraintEvaluator {

    public IdentityEvaluator(AttributeValueDTO av) {
        super(av);
    }

    @Override
    public boolean evaluate(ConstraintContext context, Map constraintConfig) throws GeneralException {
        checkAttributeValue(constraintConfig);
        validate();

        boolean result;
        ListFilterValue.Operation op = this.attributeValue.getOperator();

        if ((op == ListFilterValue.Operation.ChangedTo || op == ListFilterValue.Operation.ChangedFrom)
                && !hasChanged(context, this.attributeValue.getProperty())) {
            return false;
        }

        String value = null;
        if (op == ListFilterValue.Operation.StartsWith) {
            value = this.attributeValue.getValue().toString();
        }
        else {
            if (this.attributeValue.getValue() instanceof Map) {
                value = getIdentityNameFromMapValue(context, (Map)this.attributeValue.getValue());
            }
            else if (this.attributeValue.getValue() instanceof String) {
                value = this.attributeValue.getValue().toString();
            }
        }

        Identity prevIdentity = context.getPrevIdentity();
        Identity newIdentity = context.getNewIdentity();

        String attrValue = (op == ListFilterValue.Operation.ChangedFrom)
            ? this.getAttributeAsIdentityName(prevIdentity, this.attributeValue.getProperty())
            : this.getAttributeAsIdentityName(newIdentity, this.attributeValue.getProperty());

        if (attrValue == null) {
            return false;
        }

        switch(op) {
            case Equals:
            case ChangedTo:
            case ChangedFrom:
                result = attrValue.equals(value);
                break;
            case NotEquals:
                result = !attrValue.equals(value);
                break;
            case StartsWith:
                result = attrValue.startsWith(value);
                break;
            default:
                throw new GeneralException("Unsupported string comparison operator '" + op + "'");
        }

        return result;
    }

    public void validate() throws GeneralException {
        super.validate();

        if (!attributeValue.hasValue() ||
                !(attributeValue.getValue() instanceof Map || attributeValue.getValue() instanceof String)) {
            throw new GeneralException("A Map value is required inside " + AttributeValueDTO.ATTR_ATTRIBUTE_VALUE);
        }
    }

    /**
     * Return an identity attribute as an identity name string
     *
     * @param identity The identity to use
     * @param attrName The attribute to return as an identity name
     * @return The attribute value
     * @throws GeneralException
     */
    private String getAttributeAsIdentityName(Identity identity, String attrName) throws GeneralException {
        String identityName = null;

        // there is trickery in getAttribute() to handle "manager" and "administrator" -- it
        // will return the string name of it
        Object value = identity.getAttribute(attrName);
        if (value instanceof String) {
            identityName = (String)value;
        }
        else if (value instanceof Identity){
            identityName = ((Identity)value).getName();
        }
        return identityName;
    }

    /**
     * Get the name of the Identity that is identified by the given map
     * @param context
     * @param identityMap the Map specifying an identity
     * @return the name of the identity specified by the map, or null if none found
     * @throws GeneralException
     */
    private String getIdentityNameFromMapValue(ConstraintContext context, Map identityMap) throws GeneralException {
        // prefer getting name straight from map
        String identityName = (String)identityMap.get("name");
        if (Util.isNullOrEmpty(identityName)) {
            // we will have tp try using id, and getting name from the id
            String identityId = (String)identityMap.get("id");
            if (Util.isNotNullOrEmpty(identityId)) {
                identityName = ObjectUtil.getName(context.getSailPointContext(), Identity.class, identityId);
            }
        }
        return identityName;
    }
}
