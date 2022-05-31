/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.AnalyzeTabPanelAuthorizer;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.WebResourceAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.AuditEvent;
import sailpoint.object.Classification;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SyslogEvent;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.analyze.AnalyzeControllerBean;

/**
 * Resource for advanced analytics search, provided to allow for authorization of suggest requests.
 */
@Path("analyze")
public class AnalyzeResource extends BaseListResource {

    /**
     * Entitlement mining on the roles page is not an analyze tab technically, but it reuses our identity panel,
     * so just support it in the same way.
     */
    public static final String ROLE_ENTITLEMENT_ANALYSIS_PANEL = "entitlementAnalysisPanel";

    /**
     * Passthrough to suggest resource for analyze tabs, authorizes and sets up the suggest authorizer context based on the analyzeTabPanel
     * @param analyzeTabPanel Name of the tab panel to be authorized against.
     * @return SuggestResource
     * @throws GeneralException
     */
    @Path("{analyzeTabPanel}/suggest")
    public SuggestResource getSuggestResource(@PathParam("analyzeTabPanel") String analyzeTabPanel) throws GeneralException {
        if (AnalyzeControllerBean.ACCOUNT_GROUP_SEARCH_PANEL.equals(analyzeTabPanel)) {
            // SPECIAL CASE! Advanced search on account groups uses the same suggest stuff as account group search, so authorize that page here
            if (!AuthorizationUtility.isAuthorized(this, new WebResourceAuthorizer("define/groups/accountGroups.jsf"))) {
                authorize(new AnalyzeTabPanelAuthorizer(analyzeTabPanel));
            }
        } else {
            // Normal case, authorize the tab panel.
            authorize(new AnalyzeTabPanelAuthorizer(analyzeTabPanel));
        }
        return new SuggestResource(this, getSuggestAuthorizerContext(analyzeTabPanel));
    }

    /**
     * Gets the SuggestAuthorizerContext for the given tab panel name, to specify what objects and columns are allowed through.
     * The objects and columns must match up with the DistintRestSuggest uses in the js for the tab panel.
     * @param analyzeTabPanel Name of the tab panel
     * @return SuggestAuthorizerContext
     */
    private SuggestAuthorizerContext getSuggestAuthorizerContext(String analyzeTabPanel) {
        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        List<String> columnNames;

        // Everyone can access identities
        authorizerContext.add(Identity.class.getSimpleName());

        switch (analyzeTabPanel) {
            case AnalyzeControllerBean.ACCOUNT_GROUP_SEARCH_PANEL:
                columnNames = new ArrayList<>();
                columnNames.add("attribute");
                columnNames.add("displayableName");
                addExtendedAttributes(columnNames, ManagedAttribute.class);
                authorizerContext.add(ManagedAttribute.class.getSimpleName(), false, columnNames.toArray(new String[0]));
                authorizerContext.add(Classification.class.getSimpleName(), true);
                break;
            case AnalyzeControllerBean.AUDIT_SEARCH_PANEL:
                authorizerContext.add(AuditEvent.class.getSimpleName(), false, "action", "source", "attributeName", "attributeValue", "target", "accountName", "clientHost", "serverHost", "interface")
                        .add(Link.class.getSimpleName(), false, "instance")
                        .add(Application.class.getSimpleName(), false, "name");
                break;
            case AnalyzeControllerBean.IDENTITY_SEARCH_PANEL:
            case ROLE_ENTITLEMENT_ANALYSIS_PANEL:

                // Identity columns
                columnNames = new ArrayList<>();
                columnNames.add("displayName");
                columnNames.add("email");
                columnNames.add("manager.displayName");
                columnNames.add("administrator.displayName");
                addExtendedAttributes(columnNames, Identity.class);
                authorizerContext.add(Identity.class.getSimpleName(), false, columnNames.toArray(new String[0]));

                //Link columns
                columnNames = new ArrayList<>();
                columnNames.add("instance");
                addExtendedAttributes(columnNames, Link.class);
                authorizerContext.add(Link.class.getSimpleName(), false, columnNames.toArray(new String[0]));

                authorizerContext.add(ManagedAttribute.class.getSimpleName(), false, "application.name");
                authorizerContext.add(IdentityEntitlement.class.getSimpleName(), false, "value");

                break;
            case AnalyzeControllerBean.IDENTITY_REQUEST_SEARCH_PANEL:
                authorizerContext.add(Link.class.getSimpleName(), false, "instance");
                break;
            case AnalyzeControllerBean.LINK_SEARCH_PANEL:
                authorizerContext.add(Link.class.getSimpleName(), false, "instance", "nativeIdentity");
                authorizerContext.add(Application.class.getSimpleName(), false, "name");
                break;
            case AnalyzeControllerBean.SYSLOG_SEARCH_PANEL:
                authorizerContext.add(SyslogEvent.class.getSimpleName(), false, "eventLevel", "server", "username", "classname");
                break;
            case AnalyzeControllerBean.ROLE_SEARCH_PANEL:
                authorizerContext.add(Classification.class.getSimpleName(), true);
                break;
            case AnalyzeControllerBean.ACTIVITY_SEARCH_PANEL:
            case AnalyzeControllerBean.CERTIFICATION_SEARCH_PANEL:
            case AnalyzeControllerBean.PROCESS_INSTRUMENTATION_SEARCH_PANEL: ;
                break;

        }

        return authorizerContext;
    }

    private void addExtendedAttributes(List<String> columnNames, Class clazz) {
        List<ObjectAttribute> extendedAttributes = ObjectConfig.getObjectConfig(clazz).getExtendedAttributeList();
        for (ObjectAttribute objectAttribute : Util.iterate(extendedAttributes)) {
            columnNames.add(objectAttribute.getName());
            if (ObjectAttribute.TYPE_IDENTITY.equals(objectAttribute.getType())) {
                columnNames.add(objectAttribute.getName() + ".displayName");
                columnNames.add(objectAttribute.getName() + ".name");
            }
        }
    }
}
