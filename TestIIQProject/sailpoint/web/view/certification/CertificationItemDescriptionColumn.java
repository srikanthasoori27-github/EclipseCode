/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.api.ViolationDetailer;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * ViewColumn to get the description of a Certification Item 
 */
public class CertificationItemDescriptionColumn extends CertificationItemColumn {

    private static final Log log = LogFactory.getLog(CertificationItemDescriptionColumn.class);
    
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_CERT_ITEM_TYPE);
        cols.add(COL_EXCEPTION_ENTITLEMENTS);
        cols.add(COL_ROLE);
        cols.add(COL_POLICY_VIOLATION);
        cols.add(COL_ID);
        return cols;
    }
    
    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        String description = null;

        // This should not happen but we got a unreproducible NPE here once so
        // might as well be safe.
        if (row.get(COL_CERT_ITEM_TYPE) == null)
            return null;

        CertificationItem.Type type = (CertificationItem.Type) row.get(COL_CERT_ITEM_TYPE);
        switch (type) {
            case AccountGroupMembership: 
            case Exception: 
            case DataOwner:

                EntitlementSnapshot snap = (EntitlementSnapshot) row.get(COL_EXCEPTION_ENTITLEMENTS);
                if (snap != null) {
                    SailPointContext context = getContext().getSailPointContext();

                    Application app = snap.getApplicationObject(context);
                    CertificationEntity entity = context.getObjectById(CertificationEntity.class,
                            (String)row.get(COL_CERT_ENTITY_ID));

                    List<String> values = new ArrayList<String>();

                    if ((null != snap.getAttributes()) && (!snap.getAttributes().isEmpty())) {
                        for (Map.Entry<String,Object> entry : snap.getAttributes().entrySet()) {
                            if (entry.getValue() != null) {
                                for (Object val :  Util.asList(entry.getValue())) {
                                    String desc = null;

                                    if (entity.getType() == CertificationEntity.Type.AccountGroup) {
                                        desc = getAccountGroupEntitlementDescription(entity, app, entry.getKey(), val.toString());
                                    }
                                    // For other entity types or as a fallback if we couldn't retrieve a description.
                                    if (Util.isNullOrEmpty(desc)) {
                                        desc = getEntitlementDescription(getLocale(), app, entry.getKey(), val);
                                    }

                                    values.add(desc);
                                }
                            }
                        }

                        // DO NOT use the Util.listToCSV() methods here - creates issues with too many quotes
                        description = Util.join(values, ",");
                    }

                    if ((null != snap.getPermissions()) && (!snap.getPermissions().isEmpty())) {
                        for (Permission p : snap.getPermissions()) {
                            values.add(getEntitlementDescription(getLocale(), app, p.getTarget(), null));
                        }

                        // DO NOT use the Util.listToCSV() methods here - creates issues with too many quotes
                        description = Util.join(values, ",");
                    }
                }
                break;
            case Bundle:
                Bundle bundle = getRole(row);
                if (bundle != null) {
                    description = bundle.getDescription(getLocale());
                }
                break;
            case PolicyViolation:
                PolicyViolation violation = getPolicyViolation(row);
                if (violation != null) {
                    ViolationDetailer violationDetailer = new ViolationDetailer(getSailPointContext(), violation, getLocale(), getTimeZone());
                    description = violationDetailer.getConstraint();
                    if (Util.isNullOrEmpty(description)) {
                        // this is actually the policy description
                        description = violationDetailer.getConstraintPolicy();
                    }
                    if (Util.isNullOrEmpty(description)) {
                        description = violationDetailer.getConstraintDescription();
                    }
                }
                
        }

        return description;
    }

    private String getEntitlementDescription(Locale locale, Application app, String entitlement, Object value){
        String desc = null;

        if (value == null) {
            // assume permission
            desc = Explanator.getPermissionDescription(app, entitlement, locale);
            if (desc == null)
                desc = entitlement;
        }
        else {
            // assume entitlement
            desc = Explanator.getDescription(app, entitlement, value.toString(), locale);
            if (desc == null)
                desc = value.toString();
        }

        return desc;
    }

    private String getAccountGroupEntitlementDescription(CertificationEntity entity, Application app,
                                                         String attributeName, String attributeValue) {
        String desc = null;

        if (entity != null && app != null) {
            String targetId = entity.getTargetId();

            try {
                ManagedAttribute managedAttribute = getContext().getSailPointContext().getObjectById(ManagedAttribute.class, targetId);
                Schema schema = app.getSchema(managedAttribute.getType());
                AttributeDefinition def = schema.getAttributeDefinition(attributeName);
                if (def.isEntitlement()) {
                    desc = Explanator.getDescription(app.getId(), attributeName, attributeValue, getLocale(), def.getSchemaObjectType());
                }
            } catch (Exception e) {
                log.debug("Unable to retrieve description for managed attribute", e);
            }
        }

        return desc;
    }
}
