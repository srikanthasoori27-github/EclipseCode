package sailpoint.api.certification;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.Certification.SelfCertificationAllowedLevel;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Identity;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Combine logic for checking for self certification into a single class.
 *
 * To clarify, the isSelfCertify functions are checking to see if the identity and items would RESULT in a self certification,
 * not whether the identity is ABLE to self certify those items.
 * 
 * @author matt.tucker
 */
public class SelfCertificationChecker {
    
    private SailPointContext context;
    private String certificationId;
    private Certification certification;
    private SelfCertificationAllowedLevel selfCertAllowedLevel;

    /**
     * Check system configuration to see if we allow self certification for the given identity
     * @param identity The identity to check
     * @param context SailPointContext
     * @return true if self certification is allowed
     * @throws GeneralException
     */
    public static boolean isSelfCertifyAllowed(Identity identity, SailPointContext context) throws GeneralException {
        return isSelfCertifyAllowed(identity, getAllowedLevel(context, null));
    }

    /**
     * Check the given self certification level against the given identity to see if we allow self certification for them
     * @param identity The identity to check
     * @param selfCertAllowedLevel SelfCertificationAlloweLevel to check
     * @return true if self certification is allowed
     */
    public static boolean isSelfCertifyAllowed(Identity identity, SelfCertificationAllowedLevel selfCertAllowedLevel) {
        if (SelfCertificationAllowedLevel.All.equals(selfCertAllowedLevel) || Capability.hasSystemAdministrator(identity.getCapabilities())) {
            return true;
        } else if (SelfCertificationAllowedLevel.CertificationAdministrator.equals(selfCertAllowedLevel)) {
            return CertificationAuthorizer.isCertificationAdmin(identity);
        }

        return false;
    }

    /**
     * Get the self certification allowed level, either from the given certification or from the system configuration
     * @param context SailPointContext
     * @param certification Possibly null certification
     * @return SelfCertificationAllowedLevel
     * @throws GeneralException
     */
    private static SelfCertificationAllowedLevel getAllowedLevel(SailPointContext context, Certification certification) throws GeneralException {
        if (certification == null || certification.getCertificationDefinition(context) == null) {
            return SelfCertificationAllowedLevel.valueOf(context.getConfiguration().getString(Configuration.ALLOW_SELF_CERTIFICATION));
        } else {
            return certification.getCertificationDefinition(context).getSelfCertificationAllowedLevel(context);
        }
    }


    /**
     * Constructor
     * @param context SailPointContext
     * @param certificationId ID of the certification being checked
     */
    public SelfCertificationChecker(SailPointContext context, String certificationId) {
        this.context = context;
        this.certificationId = certificationId;
    }

    /**
     * Constructor
     * @param context SailPointContext
     * @param cert The certification being checked.
     */
    public SelfCertificationChecker(SailPointContext context, Certification cert) {
        this.context = context;
        this.certification = cert;
        this.certificationId = cert.getId();
    }

    /**
     * Check if self certification is allowed for everyone
     * @return True if allowed for all, otherwise false.
     * @throws GeneralException
     */
    public boolean isAllSelfCertifyAllowed() throws GeneralException {
        return SelfCertificationAllowedLevel.All.equals(getSelfCertAllowedLevel());
    }

    /**
     * Check if self certification is allowed for the provided identity
     * @param identity The identity to check for self certification permission
     * @return true if self certification is allowed
     * @throws GeneralException
     */
    public boolean isSelfCertifyAllowed(Identity identity) throws GeneralException {
        return isSelfCertifyAllowed(identity, getSelfCertAllowedLevel());
    }

    /**
     * Check if any item or entity in the certification targets the provided identity
     * @param identity Identity to check for self certification
     * @return true if anything in the cert is a self certification
     * @throws GeneralException
     */
    public boolean isSelfCertify(Identity identity) throws GeneralException { 
        return isSelfCertify(identity, null);
    }

    /**
     * Check if any of the provided items or entities target the provided identity
     * @param identity Identity to check for self certification
     * @param items List of items and/or entities to check against identity
     * @return true if any item or entity in the list targets the identity 
     * @throws GeneralException
     */
    public boolean isSelfCertify(Identity identity, List<AbstractCertificationItem> items) throws GeneralException {
        //If we allow it, then its cool.
        if (isSelfCertifyAllowed(identity)) {
            return false;
        }
        
        boolean selfCertify = false;
        if (items == null) {
            //With no items, we will check the entire certitification
            Class<? extends AbstractCertificationItem> itemType = isIdentityTargetOnCertificationItems() ? CertificationItem.class : CertificationEntity.class;
            selfCertify =  isSelfCertify(identity, null, itemType);
        } else {
            //We can have a mix of entities and items, so need to split them up and check each set separately.
            List<CertificationItem> certItems = new ArrayList<CertificationItem>();
            List<CertificationEntity> certEntities = new ArrayList<CertificationEntity>();

            for (AbstractCertificationItem item : items) {
                if (item instanceof CertificationItem) {
                    certItems.add((CertificationItem)item);
                } else if (item instanceof CertificationEntity) {
                    certEntities.add((CertificationEntity)item);
                }
            }

            if (!Util.isEmpty(certItems)) {
                selfCertify = isSelfCertify(identity, ObjectUtil.getObjectIds(certItems), CertificationItem.class);
            }
            if (!selfCertify && !Util.isEmpty(certEntities)) {
                selfCertify = isSelfCertify(identity, ObjectUtil.getObjectIds(certEntities), CertificationEntity.class);
            }
        }
        
        return selfCertify;
    }

    /**
     * Check if any of the ids refer to either items or entities targeting the identity
     * @param identity Identity to check against items
     * @param ids Ids to check 
     * @param itemType CertificationItem or CertificationEntity
     * @return true if any targets the identity
     * @throws GeneralException
     */
    public boolean isSelfCertify(Identity identity, List<String> ids, 
                                 Class<? extends AbstractCertificationItem> itemType) throws GeneralException {
        return (!isSelfCertifyAllowed(identity) && getIdentityTargetIds(identity, ids, itemType, 1).size() > 0);
    }

    /**
     * Gets the list of item or entity id's that target the given identity
     * @param identity Identity to check
     * @param ids Ids to check
     * @param itemType CertificationItem or CertificationEntity
     * @return List of ids
     * @throws GeneralException
     */
    public List<String> getIdentityTargetIds(Identity identity, List<String> ids, 
                                             Class<? extends AbstractCertificationItem> itemType) throws GeneralException {
        return getIdentityTargetIds(identity, ids, itemType, 0);
    }

    /**
     * Get the list of item or entity id's that target the given identity
     * @param identity Identity to check
     * @param ids Ids to check
     * @param itemType CertificationItem or CertificationEntity
     * @param maxNumberOfIds Maximum number of results in list
     * @return List of ids
     * @throws GeneralException
     */
    private List<String> getIdentityTargetIds(Identity identity, List<String> ids,
                                             Class<? extends AbstractCertificationItem> itemType, int maxNumberOfIds) throws GeneralException {
        
        List<String> selfCertIds = new ArrayList<String>();
        Certification cert = getCertification();
        Class<? extends AbstractCertificationItem> queryItemType = itemType;
        // Can only be self-certification if option is set and cert is for identities
        if (cert != null && cert.isCertifyingIdentities()) {
            String itemIdPropertyName;
            String identityPropertyName;
            if (isIdentityTargetOnCertificationItems() &&
                    CertificationEntity.class.equals(itemType)) {
                // If certifying Account Group Membership entities, the entity will be the group,
                // so we have to look for cert items matching the decider
                queryItemType = CertificationItem.class;
                itemIdPropertyName = "parent.id";
                identityPropertyName = cert.getIdentityProperty();
            } else {
                identityPropertyName = CertificationItem.class.equals(itemType) ?
                        cert.getIdentityProperty() : "identity";
                itemIdPropertyName = "id";
            }
            
            Filter identityFilter = Filter.eq(identityPropertyName, identity.getName());

            if (Util.isEmpty(ids)) {
                //If there are no id's, we are checking the entire cert
                String certIdPropertyName = (CertificationEntity.class.equals(queryItemType)) ? "certification" : "parent.certification";
                Filter idFilter = Filter.eq(certIdPropertyName, getCertification());
                
                addSelfCertIds(Filter.and(identityFilter, idFilter), queryItemType, itemIdPropertyName, selfCertIds, maxNumberOfIds);
            } else {
                int maxQuerySize = 1000;
                //Do this in batches so we don't run into SQL limits for IN
                for (int iFrom = 0; iFrom < ids.size();) {
                    int iTo = ((iFrom + maxQuerySize) > ids.size()) ? ids.size(): iFrom + maxQuerySize;
                    Filter idFilter = Filter.in(itemIdPropertyName, ids.subList(iFrom, iTo));
                    int numberOfIds = maxNumberOfIds;
                    //only need to get as many results as would fill our list to maxNumberOfIds
                    if (numberOfIds > 0 && numberOfIds > selfCertIds.size()) {
                        numberOfIds -= selfCertIds.size();
                    }
                    addSelfCertIds(Filter.and(identityFilter, idFilter), queryItemType, itemIdPropertyName, selfCertIds, numberOfIds);
                    if (maxNumberOfIds > 0 && selfCertIds.size() >= maxNumberOfIds) {
                        //Got all we need, get out
                        break;
                    }

                    iFrom = iFrom + maxQuerySize;
                }
            }
        }

        return selfCertIds;
    }

    /**
     * Helper method to query for items or entity's based on filter provided and add to list of ids
     * @param filter Filter to query for items
     * @param itemType CertificationItem or CertificationEntity
     * @param itemIdPropertyName Property to get that will have the id we want in the list
     * @param selfCertIds List to put results in
     * @param maxNumberOfIds Limit of result count 
     * @throws GeneralException
     */
    private void addSelfCertIds(Filter filter, Class<? extends AbstractCertificationItem> itemType, 
                                String itemIdPropertyName, List<String> selfCertIds, int maxNumberOfIds) 
    throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(filter);
        if (maxNumberOfIds != 0) {
            ops.setResultLimit(maxNumberOfIds - selfCertIds.size());
        }

        Iterator<Object[]> foundIds = getContext().search(itemType, ops, itemIdPropertyName);
        if (foundIds != null) {
            while (foundIds.hasNext()) {
                Object[] foundIdObject = foundIds.next();
                String foundId = (String) foundIdObject[0];
                if (!selfCertIds.contains(foundId)) {
                    selfCertIds.add(foundId);
                }
            }
        }
    }

    /**
     * Helper to check if identity target will be on certification item instead of the identity.  
     * @return true if identity target is on certification item
     * @throws GeneralException
     */
    private boolean isIdentityTargetOnCertificationItems() throws GeneralException {
        Certification cert = getCertification();
        return (Certification.Type.AccountGroupMembership.equals(cert.getType()) ||
                Certification.Type.DataOwner.equals(cert.getType()));
    }

    private SailPointContext getContext() {
        return this.context;
    }

    private Certification getCertification() throws GeneralException {
        if (this.certification == null) {
            this.certification = getContext().getObjectById(Certification.class, this.certificationId);
        }

        return this.certification;
    }

    private SelfCertificationAllowedLevel getSelfCertAllowedLevel() throws GeneralException {
        if (this.selfCertAllowedLevel == null) {
            this.selfCertAllowedLevel = getAllowedLevel(getContext(), getCertification());
        }

        return this.selfCertAllowedLevel;
    }
}
