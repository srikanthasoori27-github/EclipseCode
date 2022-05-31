/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;
                                     
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.web.messages.MessageKeys;
import net.sf.jasperreports.engine.fill.JRIncrementerFactory;
import net.sf.jasperreports.engine.fill.JRIncrementer;
import net.sf.jasperreports.engine.fill.JRFillVariable;
import net.sf.jasperreports.engine.fill.AbstractValueProvider;
import net.sf.jasperreports.engine.JRException;

/**
 * This class keeps track of the count of the number of things
 * processed by the CertificationDetailReport.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class CertificationDetailReportStatistics {

    public final static String ITEM_TYPE_ACCT_GRP_PERM = "AccountGroupPermissions";

    public Set<String> entityIds;

    // list of unique certifications in this report
    private Map<Certification.Type, Set<String>> certifications;

    private Map<Certification.Type, Integer> totalEntities;

    private Map<Certification.Type, Set<String>> totalUniqueEntities;

    // List of unique entities in this report, stored by entity type
    private Map<CertificationEntity.Type, Set<String>> entities;

    // count of decisions made for each certification item type and each certification action
    private Map<String, Map<CertificationAction.Status, Integer>> entitlementDecisions;

    
    /**
     * Creates a new instances and initializes the collections used to store counts.
     */
    public CertificationDetailReportStatistics() {

        certifications = new HashMap<Certification.Type, Set<String>>();
        totalEntities = new HashMap<Certification.Type, Integer>();
        totalUniqueEntities = new HashMap<Certification.Type, Set<String>>();
        entityIds = new HashSet<String>();

        for(Certification.Type type : Certification.Type.values()){
            certifications.put(type, new HashSet<String>());
            totalEntities.put(type, 0);
            totalUniqueEntities.put(type, new HashSet<String>());
        }

        entities = new HashMap<CertificationEntity.Type, Set<String>>();
        for (CertificationEntity.Type type : CertificationEntity.Type.values()){
            entities.put(type, new HashSet<String>());
        }

        entitlementDecisions = new HashMap<String, Map<CertificationAction.Status, Integer>>();
        for (CertificationItem.Type type : CertificationItem.Type.values()){
            Map<CertificationAction.Status, Integer> actions = new HashMap<CertificationAction.Status, Integer>();
            for (CertificationAction.Status status : CertificationAction.Status.values()){
                actions.put(status, 0);
            }
            actions.put(null, 0); // for undecided items
            entitlementDecisions.put(type.toString(), actions);
        }

        // account group permissions items do not have a type, so we'll fake it here so we can report on them
        Map<CertificationAction.Status, Integer> actions = new HashMap<CertificationAction.Status, Integer>();
        for (CertificationAction.Status status : CertificationAction.Status.values()){
            actions.put(status, 0);
        }
        actions.put(null, 0); // for undecided items
        entitlementDecisions.put(ITEM_TYPE_ACCT_GRP_PERM, actions);

    }

    private boolean isAccountGroupPermission(CertificationItem item){
        return CertificationEntity.Type.AccountGroup.equals(item.getParent().getType()) &&
                    !CertificationItem.Type.AccountGroupMembership.equals(item.getType());
    }

    /**
     * Takes a CertificationEntity and updates the counts of statistics stored on this
     * bean.
     *
     * @param entity The entity to evaluate
     * @return An empty string. This is here so that we can get Jasper to execute
     *   this method in the report detail.
     */
    public String add(CertificationEntity entity){

        // make sure we don't count an entity twice if it is spanning records.
        // todo why is jasper doing this?
        if (entityIds.contains(entity.getId()))
            return "";

        entityIds.add(entity.getId());

        Set<String> certs = certifications.get(entity.getCertification().getType());
        certs.add(entity.getCertification().getId());

        int currentTotalEntities = totalEntities.get(entity.getCertification().getType()) + 1;
        totalEntities.put(entity.getCertification().getType(), currentTotalEntities);

        // we need some ID so we can keep track of unique entities.
        // Some older certs may not have targetIds so fall back on
        // identity or acct grp
        String uniqueId = entity.getTargetId();
        if (uniqueId == null && entity.getIdentity() != null){
            uniqueId = entity.getIdentity();
        } else if (uniqueId == null && entity.getAccountGroup() != null){
            uniqueId = entity.getAccountGroup();
        }
        
        totalUniqueEntities.get(entity.getCertification().getType()).add(uniqueId);

        for (CertificationItem item : entity.getItems()){

            // get type, if it's an acct grp permissions cert, use our fake type string
            String type = isAccountGroupPermission(item) ? ITEM_TYPE_ACCT_GRP_PERM : item.getType().toString();

            if (item.getAction() != null && item.getAction().getStatus() != null){
                Integer val = entitlementDecisions.get(type).get(item.getAction().getStatus());
                entitlementDecisions.get(type).put(item.getAction().getStatus(), ++val);
            } else if (item.getDelegation() != null){
                Integer val = entitlementDecisions.get(type).get(CertificationAction.Status.Delegated);
                entitlementDecisions.get(type).put(CertificationAction.Status.Delegated, ++val);
            }else{
                Integer val = entitlementDecisions.get(type).get(null);
                entitlementDecisions.get(type).put(null, ++val);
            }
        }

        return "";
    }

    
    public String getTypeMsg(String typeName){
        Certification.Type type = Certification.Type.valueOf(typeName);
        return type.getMessageKey();
    }

    public Integer getCertificationCount(String typeName){
        Certification.Type type = Certification.Type.valueOf(typeName);
        return certifications.get(type).size();
    }

    public Integer getEntityCount(String typeName){
        Certification.Type type = Certification.Type.valueOf(typeName);
        return totalEntities.get(type);
    }

    public Integer getUniqueEntityCount(String typeName){
        Certification.Type type = Certification.Type.valueOf(typeName);
        return totalUniqueEntities.get(type).size();
    }

    public Integer getTotalCertifications(){
        int total = 0;
        for (Certification.Type type : Certification.Type.values()){
            total += this.certifications.get(type).size();
        }
        return total;
    }

    /**
     * @return Count of the entities in this report.
     */
    public Integer getTotalEntities(){
        int total = 0;
        for (Certification.Type type : Certification.Type.values()){
            total += this.totalEntities.get(type);
        }
        return total;
    }

    /**
     * Returns count of total unique entities in the report.
     * Note that each entity is counted once across certification type.
     * So if an identity was in an manager and an app owner cert, they
     * will still only count once.
     *
     * @return Count of the unique entities in this report.
     */
    public Integer getTotalUniqueEntities(){

        Set<String> totalIdentities = new HashSet<String>();
        totalIdentities.addAll(totalUniqueEntities.get(Certification.Type.Manager));
        totalIdentities.addAll(totalUniqueEntities.get(Certification.Type.ApplicationOwner));
        totalIdentities.addAll(totalUniqueEntities.get(Certification.Type.Group));
        totalIdentities.addAll(totalUniqueEntities.get(Certification.Type.Identity));
        totalIdentities.addAll(totalUniqueEntities.get(Certification.Type.BusinessRoleMembership));
        totalIdentities.addAll(totalUniqueEntities.get(Certification.Type.Focused));

        Set<String> totalAccountGroups = new HashSet<String>();
        totalAccountGroups.addAll(totalUniqueEntities.get(Certification.Type.AccountGroupPermissions));
        totalAccountGroups.addAll(totalUniqueEntities.get(Certification.Type.AccountGroupMembership));

        Set<String> totalRoles = new HashSet<String>();
        totalRoles.addAll(totalUniqueEntities.get(Certification.Type.BusinessRoleMembership));
        totalRoles.addAll(totalUniqueEntities.get(Certification.Type.BusinessRoleComposition));
        
        Set<String> totalDataOwners = new HashSet<String>();
        totalDataOwners.addAll(totalUniqueEntities.get(Certification.Type.DataOwner));

        return totalRoles.size() + totalAccountGroups.size() + totalIdentities.size() + totalDataOwners.size();
    }


    public String getStatusMsg(String status){
        if (status == null)
            return MessageKeys.CERT_ACTION_OPEN;

        CertificationAction.Status stat= CertificationAction.Status.valueOf(status);
        return stat.getMessageKey();
    }

    public String getItemTypeMsg(String typeName){
        if  (ITEM_TYPE_ACCT_GRP_PERM.equals(typeName)){
            return MessageKeys.REPT_CERT_DECISIONS_ITEM_TYPE_ACCOUNT_GRP_PERM;
        } else if (CertificationItem.Type.valueOf(typeName) == CertificationItem.Type.DataOwner) {
            return MessageKeys.OWNED_ENTITLEMENTS;
        } else {
            CertificationItem.Type type = CertificationItem.Type.valueOf(typeName);
            return type.getMessageKey();
        }
    }

    public Integer getDecisionCount(String typeName, String statusName){
        CertificationAction.Status status = statusName != null ? CertificationAction.Status.valueOf(statusName) : null;
        
        int total = entitlementDecisions.get(typeName).get(status);
        
        //bug30302 when the status in null, we need to add any possible delegated to the total  
        if (null == status){
            total += entitlementDecisions.get(typeName).get(CertificationAction.Status.Delegated);
        }
        return total;
    }

    
    public Integer getStatusTotal(String statusName){

        CertificationAction.Status status = statusName != null ?
                CertificationAction.Status.valueOf(statusName) : null;

        int total = 0;
        for(CertificationItem.Type type : CertificationItem.Type.values()){
            total += entitlementDecisions.get(type.name()).get(status);
            //bug30302 when the status in null, we need to add any possible delegated to the total
            if (null == status){
                total += entitlementDecisions.get(type.name()).get(CertificationAction.Status.Delegated);
            }
        }

        total += entitlementDecisions.get(ITEM_TYPE_ACCT_GRP_PERM).get(status);

        return total;
    }

    public Integer getDecisionTotal(){
        int total = 0;
        for(CertificationItem.Type type : CertificationItem.Type.values()){
            for (CertificationAction.Status status : CertificationAction.Status.values()){
                total += entitlementDecisions.get(type).get(status);
            }
        }

        return total;
    }

    /**
     *
     */
    public static class CertificationDetailReportStaticsFactory implements JRIncrementerFactory{
        public JRIncrementer getIncrementer(byte calculation) {
            return new CertificationDetailReportStaticsIncrementer();
        }
    }

    /**
     *
     */
    public static class CertificationDetailReportStaticsIncrementer implements JRIncrementer{
        public Object increment(JRFillVariable variable, Object expressionValue, AbstractValueProvider valueProvider) throws JRException {

            CertificationDetailReportStatistics stats =
                    (CertificationDetailReportStatistics)variable.getEstimatedValue();

            if (stats == null)
                stats = new CertificationDetailReportStatistics();

            if (expressionValue != null)
                stats.add((CertificationEntity)expressionValue);

            return stats;
        }
    }

}
