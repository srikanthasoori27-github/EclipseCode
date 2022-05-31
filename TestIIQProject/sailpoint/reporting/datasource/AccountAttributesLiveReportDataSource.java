/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.LiveReport;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Scope;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * Datasource for Account by Attribute Live Report
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class AccountAttributesLiveReportDataSource extends ProjectionDataSource implements JavaDataSource {

    private static final Log log = LogFactory.getLog(AccountAttributesLiveReportDataSource.class);

    private static final String ARG_SHOW_SCOPES = "showScopes";
    private static final String ARG_APPS = "identityApplication";

    private static String COL_ID = "__id";

    private SailPointContext context;
    private String currentIdentityId;
    private Iterator<EntitlementResult> entitlements;
    private EntitlementResult entitlement;
    private List<String> applicationIdList;
    private boolean showScopes;

    private String noValueMsg;

    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                           String groupBy, List<Sort> sort) throws GeneralException {

        this.context = context;
        super.setTimezone((TimeZone) arguments.get(JRParameter.REPORT_TIME_ZONE));
        super.setLocale((Locale) arguments.get(JRParameter.REPORT_LOCALE));

        ReportHelper helper = new ReportHelper(context, getLocale(), getTimezone());
        QueryOptions ops = helper.getFilterQueryOps(report, arguments);
        // bug #21466 - need to make this distinct to remove duplicate records
        ops.setDistinct(true);

        List<ReportColumnConfig> cols = new ArrayList<ReportColumnConfig>();
        cols.add(new ReportColumnConfig(COL_ID, "id"));
        cols.addAll(report.getGridColumns());

        showScopes = arguments.getBoolean(ARG_SHOW_SCOPES);
        applicationIdList = arguments.getList(ARG_APPS);

        /* What do we do with null values?  Just make it a string so it appears on the report? */
        this.noValueMsg = new Message(MessageKeys.NO_VALUE).getLocalizedMessage();

        init(Identity.class, ops, cols, getLocale(), getTimezone());
    }

    @Override
    public boolean next() throws JRException {

        entitlement = null;

        if (entitlements == null || !entitlements.hasNext()) {

            boolean hasEntitlements = false;
            try {
                while (!hasEntitlements && super.next()) {
                    currentIdentityId = (String) super.getFieldValue(COL_ID);
                    entitlements = buildEntitlements(currentIdentityId);

                    hasEntitlements = entitlements != null && entitlements.hasNext();

                    context.decache();
                }
            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }

        if (entitlements != null && entitlements.hasNext()) {
            entitlement = entitlements.next();
        }

        return entitlement != null;
    }

    @Override
    public Object getFieldValue(String fieldName) throws GeneralException {

        Object value = null;

        if (entitlement != null){
            if (fieldName.equals("application")) {
                value = entitlement.application;
            } else if (fieldName.equals("attribute")) {
                value = entitlement.attributeName;
            } else if (fieldName.equals("account")) {
                value = entitlement.account;
            } else if (fieldName.equals("value")) {
                value = entitlement.attributeValue;
            }
        }

        return value != null ? value : super.getFieldValue(fieldName);
    }

    public void setLimit(int startPage, int pageSize) {
        // not supported
    }

    private Iterator<EntitlementResult> buildEntitlements(String identityId) throws GeneralException {
        List<EntitlementResult> results = new ArrayList<EntitlementResult>();

        if (identityId != null) {

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identity.id", identityId));
            if (applicationIdList != null && !applicationIdList.isEmpty())
                ops.add(Filter.in("application.id", applicationIdList));

            Iterator<Object[]> linkIter = context.search(Link.class, ops,
                    Arrays.asList("application.name", "nativeIdentity", "attributes"));
            while (linkIter.hasNext()) {
                Object[] row = linkIter.next();
                String application = (String) row[0];
                String nativeIdentity = (String) row[1];
                Attributes attrs = (Attributes) row[2];
                if (attrs != null) {
                    for (String key : (Set<String>) attrs.keySet()) {
                        Object value = attrs.get(key);
                        addEntitlement(results, key, value, application, nativeIdentity);
                    }

                    // this is just like calling Link.getPermissions but we dont have to bring the full object in
                    List<Permission> perms = (List<Permission>) attrs.get(Connector.ATTR_DIRECT_PERMISSIONS);
                    if (perms != null) {
                        for (Permission perm : perms) {
                            String value = perm.getRights();
                            addEntitlement(results, perm.getTarget(), value, application, nativeIdentity);
                        }
                    }

                }
            }

            if (showScopes) {

                Identity identity = context.getObjectById(Identity.class, currentIdentityId);

                // If this identity has capabilities get them and add them to the report.
                StringBuilder caps = new StringBuilder();

                for (Capability cap : identity.getCapabilities()) {
                    Message msg = new Message(cap.getDisplayableName());
                    caps.append(msg.getLocalizedMessage() + ",");
                }
                if (caps.length() > 0) {
                    addEntitlement(results, MessageKeys.CAPABILITIES,
                            caps.substring(0, caps.length() - 1), MessageKeys.IDENTITY_IQ, "");
                }

                // Find assigned (controlled) scopes and add them to the report
                try {
                    StringBuilder scopes = new StringBuilder();
                    for (Scope scope : identity.getEffectiveControlledScopes(getContext().getConfiguration())) {
                        Message msg = new Message(scope.getDisplayableName());
                        scopes.append(msg.getLocalizedMessage() + ",");
                    }
                    if (scopes.length() > 0) {
                        addEntitlement(results, MessageKeys.AUTHORIZED_SCOPES,
                                scopes.substring(0, scopes.length() - 1), MessageKeys.IDENTITY_IQ, "");
                    }
                } catch (GeneralException ge) {
                    log.error("GeneralException caught while trying to retrieve scopes for identities: " + ge.getMessage());
                }
            }
        }
        return results.iterator();
    }

    public void addEntitlement(List<EntitlementResult> results, String key, Object value, String application, String account) {
        if (value == null) {
            value = this.noValueMsg;
        }

        if (key != null && key.toLowerCase().contains("password")) {
            results.add(new EntitlementResult(application, key,  "********", account));
        } else if (value instanceof ArrayList) {
            List<Object> values = (ArrayList) value;
            for (Object val : values) {
                results.add(new EntitlementResult(application, key, val != null ? val.toString() : this.noValueMsg, account));
            }
        } else {
            results.add(new EntitlementResult(application, key, value.toString(), account));
        }
    }

    private class EntitlementResult {
        String application;
        String attributeName;
        String attributeValue;
        String account;

        public EntitlementResult(String application, String attributeName, String attributeValue, String account) {
            this.application = application;
            this.account = account;
            this.attributeName = attributeName;
            this.attributeValue = attributeValue;
        }
    }


}
