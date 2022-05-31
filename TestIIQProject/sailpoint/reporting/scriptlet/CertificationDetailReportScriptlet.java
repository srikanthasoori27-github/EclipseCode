/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.scriptlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sf.jasperreports.engine.JRScriptletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.Explanator.Explanation;
import sailpoint.api.IdentityHistoryService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Difference;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.PermissionDifference;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.reporting.ReportingLibrary;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.policy.ViolationViewBean;

/**
 * User: jonathan.bryant
 * Created: 1:24:07 PM Jun 15, 2007
 */
public class CertificationDetailReportScriptlet extends BaseScriptlet {
    
    private static final Log log = LogFactory.getLog(CertificationDetailReportScriptlet.class);
    
    private class CertificationComment {
        String comment;
        Date date;
        
        public String getComment() {
            return comment;
        }

        public Date getDate() {
            return date;
        }

        public CertificationComment(String comment, Date date) {
            this.comment = comment;
            this.date = date;
        }
    }

    public static final String LINE_DELIMITER = " - ";

    /**
     *
     * Todo duplciates code in CertificationItemBean.BusinessRoleBean#calculateIsNewEntitlement
     * @see sailpoint.web.certification.CertificationItemBean.BusinessRoleBean#calculateIsNewEntitlement
     */ 
    public boolean isNewBusinessRole(CertificationEntity entity, String businessRoleName) {
        IdentityDifference idDiff = entity.getDifferences();
        if (null != idDiff) {
            if (isInAddedValues(idDiff.getBundleDifferences(), businessRoleName)) {
                return true;
            } 
            
            if (isInAddedValues(idDiff.getAssignedRoleDifferences(), businessRoleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if given value is in the added values list of the given Difference
     * @param difference Difference object, can be null
     * @param value String value to check
     * @return True if Difference is non-null and value is in the list of added values
     */
    private boolean isInAddedValues(Difference difference, String value) {
        if (difference != null) {
            List<String> addedValues = difference.getAddedValues();
            if (addedValues != null && addedValues.contains(value)) {
                return true;               
            }
        } 
        return false;
    }

    /**
     * Todo this duplicates code in CertificationItemBean.ExceptionBean.calculateIsNewEntitlement
     *
     * @param entity
     * @param snapshot
     * @return
     * @see sailpoint.web.certification.CertificationItemBean@calculateIsNewEntitlement
     */
    public boolean isNewEntitlement(CertificationEntity entity,
                                    EntitlementSnapshot snapshot) {
        String appName = snapshot.getApplication();
        Attributes<String, Object> extraAttrs = snapshot.getAttributes();
        List<Permission> extraPerms = snapshot.getPermissions();
  
        IdentityDifference idDiff = entity.getDifferences();
        if (null != idDiff) {
            if (null != idDiff && extraAttrs != null && !extraAttrs.isEmpty()) {
                for (Difference linkDiff : idDiff.getLinkDifferences(appName)) {
                    List<String> newVals = linkDiff.getAddedValues();
                    if (newVals == null)
                        newVals = new ArrayList<String>();
                    if (linkDiff.getNewValue() != null)
                        newVals.addAll(Util.stringToList(linkDiff.getNewValue()));

                    if (null != newVals && !newVals.isEmpty()) {
                        String attr = linkDiff.getAttribute();
                        List extraAttrVals = Util.asList(extraAttrs.get(attr));
                        if (null != extraAttrVals) {
                            for (String newVal : newVals) {
                                if (extraAttrVals.contains(newVal))
                                    return true;
                            }
                        }
                    }
                }
            }

            for (PermissionDifference pDiff : idDiff.getPermissionDifferences(appName)) {
                String target = pDiff.getTarget();
                List extraRights = getRights(extraPerms, target);
                String rights = pDiff.getRights();
                if (extraRights.contains(rights)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Todo this duplicates code in CertificationItemBean.ViolationBean.calculateIsNewEntitlement
     * @see sailpoint.web.certification.CertificationItemBean@calculateIsNewEntitlement
     */
    public boolean isNewPolicyViolation(Resolver context, CertificationEntity identity,
                                        PolicyViolation policyViolation) throws GeneralException {

        IdentityDifference idDiff = identity.getDifferences();
        if (null != idDiff)
        {
            Difference diff = idDiff.getPolicyViolationDifferences();
            if (null != diff)
            {
                String name = policyViolation.getDisplayableName();
                List<String> newViolations = diff.getAddedValues();
                return ((null != newViolations) && newViolations.contains(name));
            }
        }
        return false;
    }
    
    private String getCommenter(Resolver context, String name, Locale locale) {
        Message msg = new Message(MessageKeys.UNKNOWN);
        String commenter = msg.getLocalizedMessage(locale, null);
        
        try {
            if(name!=null) {
                commenter = name;
                Identity identity = context.getObjectByName(Identity.class, name);
                if(identity!=null)
                    commenter = identity.getDisplayableName();
            }
        } catch(GeneralException ge) {
            log.warn("Unable to get Identity for commenter using name: " + name + ". Exception: " + ge.getMessage());
        }
        return commenter;
    }
    
    /** Gets all of the various comments for this item from their various sources **/
    
    public String getComments(Resolver context, CertificationAction action, CertificationEntity entity, Locale locale) 
        throws GeneralException {
        List<CertificationComment> comments = new ArrayList<CertificationComment>();
        
        /** Get any comments for any delegations **/
        if(entity!=null && entity.getDelegation()!=null) {
            CertificationDelegation delegation = entity.getDelegation();
            if(delegation.getComments()!=null && !delegation.getComments().equals("")) {
                Message msg = new Message(MessageKeys.REPT_CERT_DECISION_COMMENT_DELEGATION, 
                        getCommenter(context, delegation.getActorName(), locale), Util.dateToString(delegation.getCreated(),"M/d/y"));
                
                CertificationComment comment 
                    = new CertificationComment(msg.getLocalizedMessage(locale, null) + ": " + delegation.getComments(), delegation.getCreated());
                comments.add(comment);
            }
            
            if(delegation.getCompletionComments()!=null && !delegation.getCompletionComments().equals("")) {
                Message msg = new Message(MessageKeys.REPT_CERT_DECISION_COMMENT_DELEGATION_COMPLETED,  
                        getCommenter(context, delegation.getOwnerName(), locale),Util.dateToString(delegation.getModified(),"M/d/y"));

                CertificationComment comment 
                    = new CertificationComment(msg.getLocalizedMessage(locale, null) + ": " + delegation.getCompletionComments(), delegation.getModified());
                comments.add(comment);
            }
        }
        
        
        if(action!=null) {
            
            /** Get any comments from the identity history on this entitlement **/
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("action.id", action.getId()));
            CertificationItem item = null;
            
            List<CertificationItem> items = context.getObjects(CertificationItem.class, qo);
            if(items!=null && !items.isEmpty()) {
                item = items.get(0);
    
                String identityId = ObjectUtil.getId((SailPointContext)context, Identity.class, item.getIdentity());
                if (identityId == null) {
                    log.warn("No Identity object found: " + item.getIdentity());
                } else {
                    IdentityHistoryService svc = new IdentityHistoryService((SailPointContext)context);
                    List<IdentityHistoryItem> historyComments = svc.getComments(identityId, item);
                    for(IdentityHistoryItem historyItem : historyComments) {
                        if( historyItem.getComments() != null ) {
                            Message msg = new Message(MessageKeys.REPT_CERT_DECISION_COMMENT, getCommenter(context, historyItem.getActor(), locale),Util.dateToString(historyItem.getEntryDate(),"M/d/y"));
        
                            CertificationComment comment 
                                = new CertificationComment(msg.getLocalizedMessage(locale, null) + ": " + historyItem.getComments(), historyItem.getEntryDate());
                            comments.add(comment);
                        }
                    }  
                }
            }
            
            /** Get any challenge comments **/
            if(item!=null && item.getChallenge()!=null) {
                CertificationChallenge challenge = item.getChallenge();
                if(challenge.getCompletionComments()!=null && !challenge.getCompletionComments().equals("")) {                    
                    
                    
                    Message msg = new Message(MessageKeys.REPT_CERT_DECISION_COMMENT_CHALLENGE,  
                            getCommenter(context, challenge.getActorName(), locale),Util.dateToString(challenge.getCreated(),"M/d/y"));
                    
                    CertificationComment comment 
                        = new CertificationComment(msg.getLocalizedMessage(locale, null) + ": " + challenge.getCompletionComments(), challenge.getCreated());
                    comments.add(comment);
                    
                }
                
                if(challenge.getDecisionComments()!=null && !challenge.getDecisionComments().equals("")) {
                    Message msg = new Message(MessageKeys.REPT_CERT_DECISION_COMMENT_CHALLENGE_COMPLETED,  
                            getCommenter(context, challenge.getDeciderName(), locale),Util.dateToString(challenge.getModified(),"M/d/y"));
                    
                    CertificationComment comment 
                        = new CertificationComment(msg.getLocalizedMessage(locale, null) + ": " + challenge.getDecisionComments(), challenge.getModified());
                    comments.add(comment);
                }
            }
            
            /** Get the comments on the action **/
            if(action.getComments()!=null) {
                Message msg = new Message(MessageKeys.REPT_CERT_DECISION_COMMENT,  
                        getCommenter(context, action.getActorName(), locale),Util.dateToString(action.getCreated(),"M/d/y"));
                
                CertificationComment comment 
                    = new CertificationComment(msg.getLocalizedMessage(locale, null) + ": " + action.getComments(), action.getCreated());
                comments.add(comment);
            }
                    
            /** Get the completion coments **/
            if(action.getCompletionComments()!=null) {
                Message msg = new Message(MessageKeys.REPT_CERT_DECISION_COMMENT,  
                        getCommenter(context, action.getActorName(), locale),Util.dateToString(action.getModified(),"M/d/y"));
                CertificationComment comment 
                    = new CertificationComment(msg.getLocalizedMessage(locale, null) + ": " + action.getCompletionComments(), action.getModified());
                comments.add(comment);
            }
        }
        
        Collections.sort(comments, new Comparator<CertificationComment>() {
            public int compare(CertificationComment a, CertificationComment b) {
                return a.getDate().compareTo(b.getDate());
            }}
        );
        
        if(comments.isEmpty()) {
            return "";
        } else {
            StringBuffer sb = new StringBuffer();
            for(CertificationComment comment : comments) {                
                sb.append(comment.getComment()+"\n");
            }
            return sb.toString();
        }
    }

    /**
     * Get all rights from the List of Permissions on the given target.
     *
     * Todo this duplicates code in CertificationItemBean.getRights
     *
     * @param perms  The List of Permissions from which to retrieve the
     *               rights.
     * @param target The target on which the rights are granted.
     * @return A non-null List of all rights on the given target pulled from
     *         the given List of Permissions.
     */
    private List<String> getRights(List<Permission> perms, String target) {
        List<String> extraRights = new ArrayList<String>();
        if (null != perms) {
            for (Permission perm : perms) {
                if (target.equals(perm.getTarget())) {
                    List<String> currRights = perm.getRightsList();
                    if (null != currRights)
                        extraRights.addAll(currRights);
                }
            }
        }
        return extraRights;
    }

    /**
     *
     * @param attributes
     * @return
     */
    public String formatEntitlementAttributes(Map<String, Object> attributes, Locale locale) {
        return formatEntitlementAttributes(null, attributes, locale);
    }    

    public String formatEntitlementAttributes(String applicationName, Map<String, Object> attributes, Locale locale) {
        if (attributes == null || attributes.size() == 0)
            return null;

        StringBuffer buf = new StringBuffer();

        List<Message> attrMessages = new ArrayList<Message>();
        if ((null != attributes) && (attributes.size() > 0)) {
            int cnt = 0;
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                List value = Util.asList(entry.getValue());
                List<String> displayValue = new ArrayList<>();
                String app = applicationName;
                //Defect 28155 we want the displayableValue for the entitlements to show up here.
                //We do not have them directly so we must reach out to the db and get them. We decided 
                //that since reports run in the background sort of, it is okay if this causing a little 
                //bit of a hit to processing
                for (Object val : value){
                    if (null != val && val instanceof String){
                        //If application name comes in Null, we will try and get it
                        //if this fails we will just move on and we will use the value not the displayablevalue
                        if (app == null) {
                            try {
                                app = (String)this.getFieldValue("exceptionEntitlements.applicationName");
                            } catch (JRScriptletException e) {
                                //Do nothing as app could be null and that is fine
                            }
                        }
                        //bli - I think this should be changed to use the Explanator cache
                        if(null != app){
                            Filter filter = Filter.and(Filter.and(Filter.eq("application.name", app),
                                    Filter.ignoreCase(Filter.eq("value", val))),
                                    Filter.eq("attribute", entry.getKey()));
                            List<ManagedAttribute> ma = null;
                            try {
                                ma = SailPointFactory.getCurrentContext().getObjects(ManagedAttribute.class, new QueryOptions(filter));
                            } catch (GeneralException e){
                                // do nothing here again as it is fine if ma is null
                            }
                            if (ma != null && ma.size()==1){
                                displayValue.add(ma.get(0).getDisplayableName());
                            } else {
                                //If we return either nothing or more than one Managed Attribute we have no idea
                                //which one to use, so we will populate with the value and not displayableValue
                                //This should be very rare but it is possible
                                displayValue.add((String)val);
                            }
                        } else {
                            // again if we are here and app was null we will just use val
                            displayValue.add((String)val);
                        }

                    }
                }
                String key = displayValue == null || displayValue.size() < 2 ?  MessageKeys.ENT_SNAP_ATTR_VAL_SUMMARY :
                    MessageKeys.ENT_SNAP_ATTR_VALS_SUMMARY;
                String delimiter = cnt > 0 ? MessageKeys.ENT_SNAP_VAL_DELIMITER : "";
                attrMessages.add(new Message(key, delimiter, displayValue, entry.getKey()));
                cnt++;
            }
        }

        String crlf = "";
        for(Message msg : attrMessages){
            buf.append(crlf + msg.getLocalizedMessage(locale, null));
            crlf = "\n";
        }

        if (buf.toString().length() == 0)
            return null;

        return buf.toString();
    }


    /**
     *
     * @param permissions
     * @return
     */
    public String formatEntitlementPermissions(List<Permission> permissions, Locale locale) {

        if (permissions == null || permissions.size() == 0)
            return null;

        StringBuffer buf = new StringBuffer();

        for (int i = 0; i < permissions.size(); i++) {
            if (permissions.get(i).getTarget() != null && permissions.get(i).getRights() != null
                    && permissions.get(i).getRights().length() > 0) {

                if (i > 0)
                    buf.append("\n");
                Message msg = permissions.get(i).getMessage();
                buf.append(LINE_DELIMITER + msg.getLocalizedMessage(locale, null));
            }
        }


        return buf.toString();
    }

    /**
     *
     * @param attributes
     * @param permissions
     * @return
     */
    public String formatEntitlements(Map attributes, List<Permission> permissions, Locale locale) {

        String attrList = formatEntitlementAttributes(attributes, locale);
        String permList = formatEntitlementPermissions(permissions, locale);

        String out = null;

        if (attrList != null && attrList.length() > 0)
            out = attrList;

        if (permList != null && permList.length() > 0) {
            if (out != null) {
                out += "\n" + permList;
            } else {
                out = permList;
            }
        }

        return out;
    }

    /**
     * Contains a lot of stolen logic from {@link #formatEntitlementAttributes} to calculate the
     * display value of a ManagedAttribute.
     *
     * This also has the same logic from that method where if it assumes if there is more than one
     * entitlement returned, it will skip it just like the above since we do not know how to display multiples.
     * The multiple issue stems from a setting called Entitlement Granularity that no longer exists
     * in the UI. In the event that we have an environment out there with that option, it is still a valid
     * case.
     * @param appName
     * @param attrs
     * @return
     * @throws GeneralException
     */
    public String getClassificationsFromAttributes(String appName, Map<String, Object> attrs) throws GeneralException {
        if (Util.isEmpty(attrs)) {
            return null;
        }
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            @SuppressWarnings("unchecked")
            List<Object> value = Util.asList(entry.getValue());

            // We want to ignore if there is more than 1 entitlement represented
            // in the additional entitlements section since we can't display it cleanly
            if (Util.size(value) == 1) {
                Object val = value.get(0);
                if (val instanceof String && null != appName) {
                    String appId = ObjectUtil.getId(SailPointFactory.getCurrentContext(), Application.class, appName);
                    if (appId != null) {
                        Explanation exp = Explanator.get(appId, (String) entry.getKey(), (String) val);
                        if (exp != null) {
                            return Util.listToCsv(exp.getClassificationDisplayableNames());
                        }
                    }
                }
            }

        }

        return null;
    }

    public String getRecommendedDecision(SailPointContext context, String recommendation, String itemId) throws GeneralException {
        return ReportingLibrary.getRecommendedDecision(context, recommendation, itemId);
    }

    public Date getRecommendationTimestamp(SailPointContext context, String itemId) throws GeneralException {
        return ReportingLibrary.getRecommendationTimestamp(context, itemId);
    }

    public String getRecommendationReasons(SailPointContext context, String itemId) throws GeneralException {
        return ReportingLibrary.getRecommendationReasons(context, itemId);
    }

    public boolean getAutoDecisionGenerated(SailPointContext context, String itemId) throws GeneralException {
        return ReportingLibrary.getAutoDecisionGenerated(context, itemId);
    }

    public boolean getAutoDecisionAccepted(SailPointContext context, String itemId) throws GeneralException {
        return ReportingLibrary.getAutoDecisionAccepted(context, itemId);
    }

    /**
     * Formats an entitlement into a descriptive string, basically a bulleted list.
     *
     * @param entitlements Snapshot to display
     * @return  Snapshot string. Returns null if the snapshot is null or if entitlements list is empty.
     */
    public String formatSnapshot(List<EntitlementSnapshot> entitlements, Locale locale) {
        if (entitlements == null || entitlements.isEmpty())
            return null;

        StringBuffer buf = new StringBuffer();

        Message noEntsMsg = new Message(
                MessageKeys.REPT_CERT_DECISIONS_SUBREPORT_MSG_NO_ENTITLEMENTS);
        String noEntsText = noEntsMsg.getLocalizedMessage(locale, null);

        for (int i = 0; i < entitlements.size(); i++) {
            EntitlementSnapshot entitlementSnapshot = entitlements.get(i);
            buf.append(entitlementSnapshot.getApplicationName() + "\n");

            String entitlementText = formatEntitlementAttributes(entitlementSnapshot.getApplicationName(), entitlementSnapshot.getAttributes(), locale);
            String permissionText = formatEntitlementPermissions(entitlementSnapshot.getPermissions(), locale);

            if (entitlementText != null && entitlementText.length() > 0) {
                buf.append(entitlementText);
                if (permissionText != null && permissionText.length() > 0)
                    buf.append("\n" + permissionText);
            } else if (permissionText != null && permissionText.length() > 0) {
                buf.append(permissionText);
            } else {
                buf.append(noEntsText);
            }
        }

        return buf.toString();
    }

    /**
     * Wraps a PolicyViolation in a ViolationBean which does some lookups to retrieve
     * constraint info, then formats the results into easily readable output.
     *
     * @param resolver  Resolver to pass to the ViolationViewBean, may not be null.
     * @param violation The violation.
     * @return ViolationViewBean for the given PolicyViolation, or null if the violation was null.
     */
    public ViolationViewBean getViolationBean(SailPointContext resolver, PolicyViolation violation) {

        if (resolver == null)
            throw new IllegalArgumentException("Context may not be null.");

        if (violation == null)
            return null;

        try {
            return new ViolationViewBean(resolver, violation);
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Filters a list of certification items by the given type.
     *
     * @param items
     * @param type
     * @return
     */
    public List filterCertificationItems(List items, String type) {

        if (type == null || type.trim().length() == 0)
            throw new IllegalArgumentException("Type name may not be null or empty");

        if (items == null)
            return Collections.EMPTY_LIST;

        ArrayList filteredItems = new ArrayList();

        for (int i = 0; i < items.size(); i++) {
            String typeName = ((CertificationItem) items.get(i)).getType().name();
            if (type.equals(typeName) || ("Exception".equals(type) && "Account".equals(typeName)))
                filteredItems.add(items.get(i));
        }

        return filteredItems;
    }

    /**
    * @param action The certification action, may be null
    * @return Description of decision, including whether it was null or bulk certified.
    */
    public String getDecisionDescription(CertificationAction action, Locale locale) {

        if (action == null || action.getStatus() == null) {
            return Internationalizer.getMessage(MessageKeys.REPT_CERT_DECISION_FLAG_MSG_NO_DECISION, locale);
        }

        Message desc = null;
        if (action.isBulkCertified()){

            desc = new Message(MessageKeys.REPT_CERT_DECISIONS_SUBREPORT_ROLE_STATUS_BULK_CERT,
                    action.getStatus().getMessageKey());
        } else {
            desc = new Message(action.getStatus().getMessageKey());
        }

        return desc.getLocalizedMessage(locale, null);
    }

    public String getClassificationDisplayableNames(SailPointContext context, List<String> classificationNames)
            throws GeneralException {
        ClassificationService service = new ClassificationService(context);
        return Util.listToCsv(service.getDisplayableNames(classificationNames));
    }
}
