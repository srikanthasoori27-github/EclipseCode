package sailpoint.web.view.certification;

import sailpoint.api.certification.RemediationAdvisor;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class RoleDetailsColumn extends CertificationItemColumn{
    
    protected final static String COL_SUBTYPE = "subType";
    protected final static String BUNDLE_ASSIGNMENT_ID = "bundleAssignmentId";
    
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_SUBTYPE);
        cols.add(BUNDLE_ASSIGNMENT_ID);

        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        Map<String, Object> details = new HashMap<String, Object>();

        if (CertificationItem.Type.Bundle.equals(getCertificationItemType(row))){


            Bundle role = getRole(row);
            boolean isAssigned = false;
            if (role != null){

                details.put("roleId", role.getId());

                RoleTypeDefinition typeDef = role.getRoleTypeDefinition();
                if (typeDef != null)
                    details.put("roleIcon", typeDef.getIcon());

                CertificationItem.SubType subType = (CertificationItem.SubType)row.get(COL_SUBTYPE);
                isAssigned = CertificationItem.SubType.AssignedRole.equals(subType);

                // todo jfb - this is expensive can we store this data?
                boolean missingReqs = false;
                if (getCertification(row).isAllowProvisioningRequirements()){
                    RemediationAdvisor advisor = new RemediationAdvisor(getSailPointContext());
                    missingReqs = !advisor.getMissingRequiredRoles(getCertificationItem(row)).isEmpty();
                }

                details.put("missingRequiredRoles", missingReqs);
                
                //Put AssignmentId on the Bundle
                details.put("assignmentId", (String)row.get(BUNDLE_ASSIGNMENT_ID));
            }

            details.put("newEntitlement", this.getCertificationItem(row).getHasDifferences());

            // todo jfb - this is for the role expando. Perhaps we could load this
            // when they click?
            Identity identity = getIdentity(row);
            details.put("identityId", identity != null ? identity.getId() : "");

            details.put("assignedRole", isAssigned);

            String roleName = (String)row.get(COL_ROLE);
            List<String> violations = new ArrayList<String>();
            QueryOptions ops = new QueryOptions(Filter.eq("type", CertificationItem.Type.PolicyViolation));
            ops.add(Filter.eq("parent.id", row.get(COL_CERT_ENTITY_ID)));
            Iterator<Object[]> violationItems = getSailPointContext().search(CertificationItem.class, ops,
                    Arrays.asList("policyViolation"));
            while(violationItems.hasNext()){
                PolicyViolation violation = (PolicyViolation)violationItems.next()[0];
                if ((violation.getRightBundles() != null && Util.csvToList(violation.getRightBundles()).contains(roleName)) ||
                        (violation.getLeftBundles() != null && Util.csvToList(violation.getLeftBundles()).contains(roleName)))
                violations.add(violation.getDisplayableName());
            }

            details.put("relatedViolations", violations);

        }

        return details;
    }

}
