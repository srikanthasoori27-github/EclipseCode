/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.logging.LogFactory;

import sailpoint.api.ProvisioningChecker;
import sailpoint.api.certification.RemediationCalculator;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.ReportParameterUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public abstract class BaseCertificationDataSource 
                extends SailPointDataSource {
    
    private static final org.apache.commons.logging.Log LOG = LogFactory.getLog(BaseCertificationDataSource.class);
    
    // Names of the object properties we will query on
    public static final String PROPERTY_EXPIRATION = "expiration";
    public static final String PROPERTY_CREATED = "created";
    public static final String PROPERTY_SIGNED = "signed";
    public static final String PROPERTY_TAGS = "tags";
    

     // Query parameters
    private Date signedStartDate;
    private Date signedEndDate;
    private Date expirationStartDate;
    private Date expirationEndDate;
    private Date createdStartDate;
    private Date createdEndDate;
    private List<String> tagIds;

    // Class variables for tracking percent complete.
    // Most derived classes iterate over CertificationItems, however
    // they choose the relevant items through logic, so there is no 
    // way to know the total item count ahead of time.  So we use the 
    // CertificationEntity count instead, which tracks pretty close. 
    // But we cant use standard SailPointDataSource object count/processed.
    private int processedEntityCount;
    private int totalEntityCount;
    private String currentEntityId;
    
    public static final String CERTIFICATION_ENTITY_KEY = "certificationEntityId";

    protected BaseCertificationDataSource( Locale locale, TimeZone timezone) {
        super(locale, timezone);
    }
    
    /**
     * @param propertyPrefix prefix to add to the properties. This allows
     * use to use the same code for Certification and CertificationEntity queries.
     * For Certification queries the prefix will be an empty string. For CertificationEntity
     * queries it will be 'certification.'
     * @return  base query including all the parameters specified in the report
     * task definition.
     */
    protected Filter getUserSpecifiedFilters(String propertyPrefix) {
        List<Filter> filters = new ArrayList<Filter>();

        Filter createdFilter = ReportParameterUtil.getDateRangeFilter(propertyPrefix + PROPERTY_CREATED,
                createdStartDate, createdEndDate);
        if (createdFilter != null)
            filters.add(createdFilter);

        Filter expirationFilter = ReportParameterUtil.getDateRangeFilter(propertyPrefix + PROPERTY_EXPIRATION,
                expirationStartDate, expirationEndDate);
        if (expirationFilter != null)
            filters.add(expirationFilter);

        Filter signedFilter = ReportParameterUtil.getDateRangeFilter(propertyPrefix + PROPERTY_SIGNED,
                signedStartDate, signedEndDate);
        if (signedFilter != null)
            filters.add(signedFilter);

        if ((null != this.tagIds) && !this.tagIds.isEmpty()) {
            List<Filter> tagFilters = new ArrayList<Filter>();
            for (String tagId : tagIds) {
                tagFilters.add(Filter.or(Filter.eq("id", tagId), Filter.eq("name", tagId)));
            }
            filters.add(Filter.collectionCondition(propertyPrefix + PROPERTY_TAGS, Filter.and(tagFilters)));
        }
        
        if (filters.isEmpty())
            return null;

        return Filter.and(filters);
    }

    /**
     * Generates message indicating the completion of a remediation. 
     * @param item
     * @param completionKey Message key to be used when generating a 'remediation complete' message.
     * @return
     * @throws GeneralException
     */
    protected String getRemediationStatus(CertificationItem item, String completionKey) throws GeneralException{

        if (item.getAction() == null || 
                !CertificationAction.Status.Remediated.equals(item.getAction().getStatus()) || 
                !item.getAction().isRemediationKickedOff())
            return "";

        boolean remediated = false;
        if (item.getAction().isRemediationCompleted()){
            remediated = true;
        } else {
            ProvisioningChecker scanner = new ProvisioningChecker(getContext());
            CertificationEntity parent = item.getParent();
            if (parent != null){
                switch(parent.getType()){
                    case DataOwner:
                        // for dataowner type the identity is per cert item not cert entity
                        Identity dataOwnerIdentity = item.getIdentity(getContext());
                        return getRemediationStatusForIdentity(parent, item, dataOwnerIdentity, scanner, completionKey);
                    case Identity:
                        Identity identity = parent.getIdentity(getContext());
                        return getRemediationStatusForIdentity(parent, item, identity, scanner, completionKey);
                    case AccountGroup:
                        remediated = isAccountGroupRemediated(item, scanner, parent);
                        break;
                    case BusinessRole:
                        Bundle role = getContext().getObjectById(Bundle.class, parent.getTargetId());
                        remediated = role == null ||
                                scanner.hasBeenExecuted(item.getAction().getRemediationDetails(), role);
                        break;
                    default:
                        throw new GeneralException("Unhandled CertificationEntity type:" + parent.getType());
                }
            }
        }

        return remediated ? getMessage(completionKey,"") : "";
    }

    private boolean isAccountGroupRemediated(CertificationItem item,
                                                        ProvisioningChecker scanner, 
                                                        CertificationEntity parent)
            throws GeneralException {

        ManagedAttribute accountGroup = parent.getAccountGroup(getContext());
        if (accountGroup == null) {return true;}
        if (item.getAction() == null || item.getAction().getRemediationDetails() == null) {return true;}
        
        return scanner.hasBeenExecuted(item.getAction().getRemediationDetails(), accountGroup);
    }

    private String getRemediationStatusForIdentity(CertificationEntity parent, 
                                                    CertificationItem item, 
                                                    Identity identity,
                                                    ProvisioningChecker scanner,
                                                    String completionKey) throws GeneralException {

        boolean remediated;
        
        try {
            remediated = identity == null ||
                    scanner.hasBeenExecuted(item.getAction().getRemediationDetails(), identity);
    
            // if this is an account revoke which has not been remediated, check to
            // see if just the entitlement has been remediated.
            if (!remediated && item.getAction().isRevokeAccount() &&
                !CertificationItem.Type.Account.equals(item.getType())){
                RemediationCalculator calc = new RemediationCalculator(getContext());
                ProvisioningPlan remedPlan =
                        calc.calculateProvisioningPlan(item, CertificationAction.Status.Remediated);
                if (remedPlan != null && scanner.hasBeenExecuted(remedPlan, identity)){
                    return getMessage(MessageKeys.REPT_CERT_ENTITLEMENT_REMOVED,"");          
                }
            }
        } catch (ProvisioningChecker.ProvisioningCheckerException pce) {
            
            String errorMsg = "ProvisioningChecker failed on item: " + item.getId() +
            " in entity: " + parent.getId() +
            " on certification " + parent.getCertification().getId();
            
            // why?
            if (pce.requestAttrValueIsNull && null != pce.attributeRequest) {
                errorMsg += " : Attribute Request value is null: " + pce.attributeRequest.toXml();
            } else if (pce.requestPermValueIsNull && null != pce.permissionRequest) {
                errorMsg += " : Permission Request value is null: " + pce.permissionRequest.toXml();
            }
            
            LOG.error(errorMsg + " : " + pce);
            return getMessage(MessageKeys.UNKNOWN);
        }
        
        return remediated ? getMessage(completionKey,"") : "";
    }


    public void setCreatedEndDate(Date createdEndDate) {
        this.createdEndDate = createdEndDate;
    }

    public void setCreatedStartDate(Date createdStartDate) {
        this.createdStartDate = createdStartDate;
    }

    public void setExpirationEndDate(Date expirationEndDate) {
        this.expirationEndDate = expirationEndDate;
    }

    public void setExpirationStartDate(Date expirationStartDate) {
        this.expirationStartDate = expirationStartDate;
    }

    public void setSignedEndDate(Date signedEndDate) {
        this.signedEndDate = signedEndDate;
    }

    public void setSignedStartDate(Date signedStartDate) {
        this.signedStartDate = signedStartDate;
    }

    public void setTagIds(List<String> tagIds) {
        this.tagIds = tagIds;
    }
    
    /**
     * Set query options and total entity count. 
     */
    @Override
    public void internalPrepare() throws GeneralException{
        qo = getQueryOptions();
        
        totalEntityCount = this.getContext().countObjects(CertificationEntity.class, qo);
        processedEntityCount = 0;
    }
    
    /**
     * Should be overridden in derived class if using entity count
     * @return QueryOptions to assign to 'qo', use for entity count.
     * @throws GeneralException
     */
    protected QueryOptions getQueryOptions() throws GeneralException {
        return new QueryOptions();
    }
    
    protected int getTotalEntityCount() {
        return totalEntityCount;
    }
    
    protected void setTotalEntityCount(int entityCount) {
        totalEntityCount = entityCount;
    }
    
    protected int getProcessedEntityCount(){
        return processedEntityCount;
    }

    protected void incrementProcessedEntityCount(){
        processedEntityCount++;
    }
    
    /**
     * Check if we have moved on to a new entity, and update progress. 
     * Should be called by derived class in InternalNext, after retrieving new item.
     * 
     * @param itemAttributes Attributes row we are looking at
     * @param updateProgress 
     */
    protected void checkProcessedEntity(Attributes itemAttributes, boolean updateProgress) {
        String entityId = itemAttributes.getString(CERTIFICATION_ENTITY_KEY);
        if (entityId != null) {
            if (currentEntityId == null) {
                currentEntityId = entityId;
            }
            else{
                if (!currentEntityId.equals(entityId)) {
                    //we have moved to a different entity
                    incrementProcessedEntityCount();
                    currentEntityId = entityId;
                }
            }
            
            //dont need it anymore, lets remove it
            itemAttributes.remove(CERTIFICATION_ENTITY_KEY);
        }
        
        //now update progress, if we want
        if (updateProgress) {
            Message msg = new Message(
                    MessageKeys.REPT_CERTIFICATION_PROGRESS_PROCESSING_ITEM, getProcessed());
            updateProgress(msg.getLocalizedMessage(getLocale(), getTimezone()), this.getProcessedEntityPercent());
        }
    }
    
    /**
     * Get the percent complete for entity count
     * @return Percent complete, or -1 if not using entity count
     */
    protected int getProcessedEntityPercent(){
        return (totalEntityCount > 0) ? Util.getPercentage(processedEntityCount, totalEntityCount) : -1;
    }
    
    /**
     * Put the entity id in the attributes row to compare later for progress update.
     * Should be called when creating Attributes row from CertificationItem. 
     * 
     * @param row Attributes row for this item
     * @param item CertificationItem we are looking at
     */
    protected void putEntityIdForPercent(Attributes row, CertificationItem item) {
        row.put(CERTIFICATION_ENTITY_KEY, item.getCertificationEntity().getId());
    }
}
