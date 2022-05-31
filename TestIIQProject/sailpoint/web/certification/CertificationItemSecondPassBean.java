/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONString;
import org.json.JSONWriter;

import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;


/**
 * This bean is used to calculate information that is too expensive to load when
 * certification items are first rendered.  This is called in a subsequent AJAX
 * request with the IDs of the CertificationItems for which more information is
 * needed.  We're sending back a JSON representation of the additional
 * information that can be used by the pages.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationItemSecondPassBean extends BaseBean {
    private static final Log log = LogFactory.getLog(CertificationItemSecondPassBean.class);
    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Simple structure holding the information loaded in the second-pass for a
     * single CertificationItem.
     */
    public static class SecondPassInfo {
        
        private String itemId;
        private boolean showRemediationDialog;
        private boolean showRevokeAccountDialog;

        public SecondPassInfo(String itemId, boolean showRemediationDialog,
                              boolean showRevokeAccountDialog) {
            this.itemId = itemId;
            this.showRemediationDialog = showRemediationDialog;
            this.showRevokeAccountDialog = showRevokeAccountDialog;
        }

        public String getItemId() {
            return this.itemId;
        }

        public boolean isShowRemediationDialog() {
            return this.showRemediationDialog;
        }

        public boolean isShowRevokeAccountDialog() {
            return this.showRevokeAccountDialog;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The IDs of the CertificationItems for which to retrieve more information.
     */
    private List<String> itemIds;
    
    /**
     * The ID of the work item the request is coming from if this is from a
     * delegation.
     */
    private String workItemId;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.  This pulls the itemIds off of the request.
     */
    public CertificationItemSecondPassBean() {
        super();

        this.itemIds = Util.csvToList(super.getRequestParameter("itemIds"));
        this.workItemId = super.getRequestParameter("workItemId");
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the second-pass information for the requested items as a JSON
     * array of SecondPassInfo objects.
     */
    public String getSecondPassInfoJSON() throws GeneralException {
       
        if (null == this.itemIds) {
            throw new GeneralException("No item ids specified.");
        }

        List<SecondPassInfo> infos = new ArrayList<SecondPassInfo>();
        Boolean userAuthorized = null;
        for (String itemId : this.itemIds) {
            CertificationItem item =
                getContext().getObjectById(CertificationItem.class, itemId);

            // check that user has the right to view this certification
            if (userAuthorized == null){
                if (isUserAuthorized(item.getCertification())){
                    userAuthorized = true;
                } else {
                    log.error("Error generatating second pass info JSON. User '"+getLoggedInUserName()+"'  " +
                            "did not have access to certification item id=" + item.getId());
                    return JsonHelper.emptyListResult(null, "contents");
                }
            }

            SecondPassInfo info = new SecondPassInfo(itemId, true, false);

            // Policy violation always shows dialog - for everything else we
            // will calculate.
            if (!CertificationItem.Type.PolicyViolation.equals(item.getType())) {
                info = calculateSecondPassInfo(item, item.getParent(), null);
            }

            infos.add(info);
        }
        
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        
        try {
            jsonWriter.object();
            jsonWriter.key("contents");
            jsonWriter.value(new SecondPassInfoSerializer(infos));
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate second pass info JSON", e);
            result = JsonHelper.emptyListResult(null, "contents");
        }
        
        return result;
    }

    public boolean isUserAuthorized(Certification cert) throws GeneralException{

        return CertificationAuthorizer.isAuthorized(cert, this.workItemId, this);
    }
    
    protected QueryOptions addScopeExtensions(QueryOptions ops){
        ops.setUnscopedGloballyAccessible(false);
        return ops;
    }

    private class SecondPassInfoSerializer implements JSONString {
        List <SecondPassInfo> info;
        
        SecondPassInfoSerializer(List<SecondPassInfo> info) {
            this.info = info;
        }
        
        public String toJSONString() {
            return JsonHelper.toJson(info);
        }
    }

    /**
     * Determine whether to show the remediation dialog for the given item.
     */
    SecondPassInfo calculateSecondPassInfo(CertificationItem item,
                                           CertificationEntity entity,
                                           String workItemId)
        throws GeneralException {

        // remediation of assigned roles is performed by IIQ internally
        if (CertificationItem.Type.Bundle.equals(item.getType()) &&
            CertificationItem.SubType.AssignedRole.equals(item.getSubType())){
            return new SecondPassInfo(item.getId(), false, false);
        }


        boolean showRemediationDialog = true;
        boolean showRevokeAcctDialog = false;

        // Using the CertificationActionBean for this since it has logic to
        // determine the remediation action, default remediator, and app.  The
        // down side is that constructing a CerticationActionBean for every
        // item is expensive.  Look in to the L2 cache to speed this up.
        CertificationActionBean cab =
            new CertificationActionBean(item, workItemId, CertificationAction.Status.Remediated);
        Configuration sysConfig = Configuration.getSystemConfig();
        boolean editable = cab.isProvisioningPlanEditable();
        
        // If auto-remediating, only show dialog if editable or forcing display
        // of the auto-remediation dialog.
        if (cab.isShowProvisioningPanel()) {
            boolean force =
                sysConfig.getBoolean(Configuration.SHOW_AUTO_REMEDIATION_DIALOG, false);
            showRemediationDialog = editable || force;
            showRevokeAcctDialog = force;
        }
        else {
            // Not able to auto-remediate.  Show dialog if editable or there is
            // not a remediator that can be calculated.
            // bug # 15319 - changed the way default remediator is calculated, role based revocations will default to 
            // a common app owner, then role owner and fall back to default remediator if one is set.
            boolean noDefaultRemediator = item.getDefaultRemediator(getContext()) == null;
            boolean noAppOwner = cab.getApplicationOwner() == null;
            // bug # 14124 - Remediation dialog was not displaying due to noDefaultRemediator always false after bug # 15319 was committed.
            // moving sysConfig check here to allow remediation dialog to appear
            boolean noRemediator = (noDefaultRemediator && noAppOwner) || sysConfig.getBoolean(Configuration.DISABLE_DEFAULT_TO_APP_OWNER_REMEDIATION, false);
            showRemediationDialog = editable || noRemediator;
            showRevokeAcctDialog = noRemediator;
        }

        return new SecondPassInfo(item.getId(), showRemediationDialog, showRevokeAcctDialog);
    }
}
