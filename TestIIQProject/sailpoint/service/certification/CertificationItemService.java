/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Iconifier;
import sailpoint.api.IdentityService;
import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.api.ViolationDetailer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.LinkInterface;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationAdvisor;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;
import sailpoint.web.view.DecisionSummary;
import sailpoint.web.view.DecisionSummaryFactory;

/**
 * Service class for CertificationItems
 */
public class CertificationItemService {

    private static final Log log = LogFactory.getLog(CertificationItemService.class);

    private SailPointContext context;
    private UserContext userContext;
    
    /**
     *
     * @param userContext User context
     */
    public CertificationItemService(UserContext userContext) {
        this.userContext = userContext;
        this.context = userContext.getContext();
    }

    /**
     * Creates an IdentityHistoryItem on the CertificationItem Identity with the provided comment text.
     *
     * @param user The Identity of the user making the comment
     * @param certItemId The id of the CertificationItem
     * @param commentText The comment text to add to a new IdentityHistoryItem.

     * @return {RequestResult} Returns RequestResult success or error message.
     */
    public RequestResult addEntitlementComment(Identity user, String certItemId, String commentText) {
        if (commentText != null && commentText.trim().length() > 0) {
            try {
                CertificationItem item = context.getObjectById(CertificationItem.class, certItemId);

                Identity identity = item.getIdentity(context);
                if (identity != null) {
                    identity.addEntitlementComment(context, item, user.getDisplayName(), WebUtil.escapeComment(commentText));
                    context.saveObject(identity);
                    context.commitTransaction();
                }
                else {
                    String errMsg = Message.error(MessageKeys.ERR_OBJ_NOT_FOUND).getLocalizedMessage(this.userContext.getLocale(), null);
                    return new RequestResult(RequestResult.STATUS_FAILURE, null, null, Arrays.asList(errMsg));
                }
            }
            catch (GeneralException e) {
                log.error(e);
                String errMsg = Message.error(MessageKeys.ERR_MISSING_PARAM).getLocalizedMessage(this.userContext.getLocale(), null);
                return new RequestResult(RequestResult.STATUS_FAILURE, null, null, Arrays.asList(errMsg));
            }
        }
        return new RequestResult(RequestResult.STATUS_SUCCESS, null, null, null);
    }

    /**
     * Get the remediation advice for the given certification item. 
     * @param item the certification item
     * @return RemediationAdviceResult
     * @throws GeneralException
     */
    public RemediationAdviceResult getViolationRemediationAdvice(CertificationItem item) throws GeneralException {
        RemediationAdvice advice = new RemediationAdvice();
        RemediationAdviceResult result = new RemediationAdviceResult();

        // Since this is coming from the certification layer, grab the setting from the cert definition.
        CertificationDefinition definition = item.getCertification().getCertificationDefinition(this.context);
        boolean includeClassifications = definition.isIncludeClassifications(this.context);

        if (!CertificationItem.Type.PolicyViolation.equals(item.getType()) ||
                item.getPolicyViolation() == null) {
            return null;
        }

        ViolationDetailer violationDetailer = new ViolationDetailer(this.context, item.getPolicyViolation(),
            this.userContext.getLocale(), this.userContext.getUserTimeZone());
        PolicyViolationAdvisor advisor =
            new PolicyViolationAdvisor(this.context, item.getPolicyViolation(), this.userContext.getLocale());
        advice.setViolationConstraint(violationDetailer.getConstraint());
        advice.setViolationSummary(violationDetailer.getSummary());
        advice.setRemediationAdvice(violationDetailer.getRemediationAdvice());

        // Set the entitlements if this is an entitlement SoD violation.
        advice.setEntitlementsToRemediate(advisor.getEntitlementViolations(item.getCertificationEntity().getId(), includeClassifications));
        
        Localizer localizer = new Localizer(this.context);
        List<String> rolesNames = new ArrayList<String>();
        List<Bundle> rightRoles = advisor.getRightBusinessRoles();
        if (rightRoles != null) {
            for (Bundle role : rightRoles) {
                String roleDesc = localizer.getLocalizedValue(role, Localizer.ATTR_DESCRIPTION, this.userContext.getLocale());
                List<String> classificationDisplayNames = includeClassifications ? role.getClassificationDisplayNames() : null;
                advice.addRightRole(role.getId(), role.getName(), role.getDisplayableName(), roleDesc, advisor.isBusinessRoleSelected(role),
                                    classificationDisplayNames);
                rolesNames.add(role.getName());
            }
        }

        List<Bundle> leftRoles = advisor.getLeftBusinessRoles();
        if (leftRoles != null) {
            for (Bundle role : leftRoles) {
                String roleDesc = localizer.getLocalizedValue(role, Localizer.ATTR_DESCRIPTION, this.userContext.getLocale());
                List<String> classificationDisplayNames = includeClassifications ? role.getClassificationDisplayNames() : null;
                advice.addLeftRole(role.getId(), role.getName(), role.getDisplayableName(), roleDesc, advisor.isBusinessRoleSelected(role),
                                   classificationDisplayNames);
                rolesNames.add(role.getName());
            }
        }

        if (!rolesNames.isEmpty()) {
            QueryOptions ops = new QueryOptions(Filter.in("bundle", rolesNames));
            ops.add(Filter.eq("parent.id", item.getCertificationEntity().getId()));
            Iterator<Object[]> roleRemovals = this.context.search(CertificationItem.class, ops,
                    Arrays.asList("id", "parent.id", "bundle", "action.status", "summaryStatus", "delegation.ownerName"));
            while (roleRemovals.hasNext()) {
                Object[] next =  roleRemovals.next();
                String itemId = (String)next[0];
                String entityId = (String)next[1];
                String role = (String)next[2];
                CertificationAction.Status status = (CertificationAction.Status)next[3];
                AbstractCertificationItem.Status summaryStatus = (AbstractCertificationItem.Status)next[4];
                String delegationOwner = (String)next[5];

                // Bug IIQETN-4580
                // Per the CertificationAction class comments, the Delegated status is not persisted in the CertificationAction
                // class.  Instead a Certification is considered Delegated if the summaryStatus is set to delegated and a
                // CertificationDelegation object is created.  In this case, we will look at the CertificationAction first and
                // if it is null, then we will look at the CertificationItem summaryStatus to see if this is indeed Delegated.
                // If so and the current Identity is not the same as the delegation owner Identity, the we will set the Delegated
                // status before returning the remediation advice to the GUI.
                if (null == status && summaryStatus == AbstractCertificationItem.Status.Delegated &&
                        null != delegationOwner && !delegationOwner.equals(context.getUserName())) {
                    status = CertificationAction.Status.Delegated;
                }
                advice.addSODRoleStatus(role, itemId, entityId, status);
            }
        }

        if (!advice.requiresRemediationInput()) {
            result.setSummary(getDecisionSummary(item, CertificationAction.Status.Remediated, null));
        }
        
        result.setAdvice(advice);
        return result;
    }

    /**
     * Get the DecisionSummary for the certification item 
     * @param item CertificationItem
     * @param status CertificationAction.Status value
     * @param additionalBundles List of Bundles to remediate along with the item
     * @return DecisionSummary object
     * @throws GeneralException
     */
    public DecisionSummary getDecisionSummary(CertificationItem item, CertificationAction.Status status,
                                       List<Bundle> additionalBundles) throws GeneralException {
        DecisionSummaryFactory summaryFactory = new DecisionSummaryFactory(this.context, this.userContext.getLoggedInUser(),
                this.userContext.getLocale(), this.userContext.getUserTimeZone());
        return summaryFactory.calculateSummary(item, status, additionalBundles);
    }

    /**
     * Update the policy violation with roles and entitlements to be revoked 
     * @param item CertificationItem
     * @param revokedRoles List<String> list of roles to revoke
     * @param revokedEntitlements List<PolicyTreeNode> List of entitlements to revoke
     * @return CertificationItem object
     * @throws GeneralException
     */
    public CertificationItem updateViolationDetails(
        CertificationItem item,
        List<String> revokedRoles,
        List<PolicyTreeNode> revokedEntitlements) throws GeneralException {

        if (item == null) {
            throw new InvalidParameterException("item");
        }
        PolicyViolation violation = item.getPolicyViolation();
        if (violation != null) {
            if (!Util.isEmpty(revokedEntitlements)) {
                violation.setEntitlementsToRemediate(revokedEntitlements);
            } else if (!Util.isEmpty(revokedRoles)) {
                violation.setBundleNamesMarkedForRemediation(revokedRoles);
            }
            item.setPolicyViolation(violation);
        }
        return item;
    }

    /**
     * Method that gets the underlying link for the certification item.  This will either be a LinkSnapshot
     * or a Link
     * @param certItem The item to get to the link for
     * @return The link
     */
    public LinkInterface getLink(CertificationItem certItem) throws GeneralException {
        LinkInterface link = null;
        if (hasLinkSnapshot(certItem)) {
            link = getLinkSnapshot(certItem);
            if (link == null) {
                link = getRealLink(certItem);
            }
        } else if (CertificationItem.Type.AccountGroupMembership.equals(certItem.getType()) || CertificationItem.Type.DataOwner.equals(certItem.getType())) {
            link = getRealLink(certItem);
        } else {
            throw new GeneralException("Certification item type '" + certItem.getType() + "' not supported");
        }
        return link;
    }

    /**
     * Returns true if the cert item is of a type that has link snapshots and has entitlement exceptions
     * @param certItem The certitem in question
     * @return true if the cert item is of a type that has link snapshots and has entitlement exceptions
     */
    private boolean hasLinkSnapshot(CertificationItem certItem) {
        return (CertificationItem.Type.Exception.equals(certItem.getType()) ||
                CertificationItem.Type.Account.equals(certItem.getType())) &&
                certItem.getExceptionEntitlements() != null;
    }

    /**
     * Retrieves the link snapshot from the cert item
     * @param certItem The item to get the snapshot from
     * @return The LinkSnapshot
     */
    private LinkSnapshot getLinkSnapshot(CertificationItem certItem) throws GeneralException {
        String app = certItem.getExceptionEntitlements().getApplication();
        String instance = certItem.getExceptionEntitlements().getInstance();
        String nativeId = certItem.getExceptionEntitlements().getNativeIdentity();
        return certItem.getLinkSnapshot(context, app, instance, nativeId);
    }

    /**
     * Attempts to find a link for a given certification item. This is used in
     * cases where the LinkSnapshot could not be retrieved from the item.
     * @param item
     * @return
     * @throws GeneralException
     */
    private Link getRealLink(CertificationItem item) throws GeneralException {

        if (item.getExceptionEntitlements() != null) {
            QueryOptions ops = new QueryOptions();
            List<Filter> filters = new ArrayList<Filter>();
            filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", item.getExceptionEntitlements().getNativeIdentity())));
            filters.add(Filter.eq("application.name", item.getExceptionEntitlements().getApplicationName()));
            if (!Util.isNullOrEmpty(item.getExceptionEntitlements().getInstance())) {
                filters.add(Filter.eq("instance", item.getExceptionEntitlements().getInstance()));
            }
            ops.add(Filter.and(filters));
            List<Link> links = context.getObjects(Link.class, ops);
            if (!links.isEmpty())
                return links.get(0);
        }

        return null;
    }

    /**
     * Returns LinkAttributesDTOs for the identity on the certification item
     * @param certItem The CertificationItem to get the identity from
     * @param start The start index to get links
     * @param limit The maximum number of links to get
     * @return LinkAttributesDTOs for the identity on the certification item
     */
    public ListResult getLinksAttributes(CertificationItem certItem, int start, int limit) throws GeneralException {
        Identity identity = certItem.getIdentity(context);
        if (identity == null) {
            throw new ObjectNotFoundException(Identity.class, certItem.getIdentity());
        }
        IdentityService identityService = new IdentityService(context);
        List<Link> links = identityService.getLinks(certItem.getIdentity(context), start, limit);
        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.eq("identity", certItem.getIdentity(context)));
        int totalLinks = context.countObjects(Link.class, ops);

        Map<String, List<Iconifier.Icon>> accountIconsByLink = new Iconifier().getAccountIconsForLinks(links);

        List<LinkAttributesDTO> linkAttributes = new ArrayList<>();
        for (Link link : links) {
            linkAttributes.add(new LinkAttributesDTO(link, accountIconsByLink.get(link.getId())));
        }

        return new ListResult(linkAttributes, totalLinks);
    }

}
