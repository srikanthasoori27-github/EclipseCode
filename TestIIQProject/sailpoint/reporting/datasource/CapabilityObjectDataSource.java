/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Datasource for the capability to identities LiveReport
 */
public class CapabilityObjectDataSource extends ProjectionDataSource implements JavaDataSource {

    private static final Log log = LogFactory.getLog(CapabilityObjectDataSource.class);
    private static final String PROP_DISPLAY_NAME = "displayName";
    private static final String PROP_IDENTITIES = "identities";
    private static final String ARG_EXCLUDE_WORKGROUPS = "excludeWorkgroups";
    private static final String ARG_EXCLUDE_INDIRECT_CAPS = "excludeIndirectCapabilities";
    /**
     * Flag to skip identities where they indirectly have a capability through a workgroup
     */
    private boolean excludeIndirectCapabilities = false;
    /**
     * Flag to skip workgroups when building a list of identities for a capability
     */
    private boolean excludeWorkgroups = false;

    @Override
    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
            String groupBy, List<Sort> sort) throws GeneralException {
        super.setTimezone((TimeZone)arguments.get(JRParameter.REPORT_TIME_ZONE));
        super.setLocale((Locale) arguments.get(JRParameter.REPORT_LOCALE));

        this.excludeIndirectCapabilities = arguments.getBoolean(ARG_EXCLUDE_INDIRECT_CAPS);
        this.excludeWorkgroups = arguments.getBoolean(ARG_EXCLUDE_WORKGROUPS);

        ReportHelper helper = new ReportHelper(context, getLocale(), getTimezone());
        QueryOptions ops = helper.getFilterQueryOps(report, arguments);

        init(Capability.class, ops, report.getGridColumns(), getLocale(), getTimezone());
    }

    @Override
    public Object getFieldValue(String fieldName) throws GeneralException {
        if( Util.isNullOrEmpty(fieldName) ) {
            throw new GeneralException( "Field name should not be null or empty" );
        }
        if (currentRow != null) {
            if (fieldName.equals(PROP_IDENTITIES)) {
                //Figure out the list of identities that have this current capability
                String capabilityId = (String) currentRow.get("id");
                List<String> identityNames = new ArrayList<>();

                if (Util.isNotNullOrEmpty(capabilityId)) {
                    QueryOptions qops = buildIdentitiesOptions(capabilityId);

                    //only care about returning the name of the identity to build the result list
                    Iterator<Object[]> it = getContext().search(Identity.class, qops, PROP_DISPLAY_NAME);

                    buildNamesList(it, identityNames);

                }
                return identityNames;
            }
        }

        return super.getFieldValue(fieldName);
    }

    /**
     * Builds the QueryOptions to return the identities that have the current capability
     * @param capabilityId The current capability id
     * @return QueryOptions for the projection search
     */
    private QueryOptions buildIdentitiesOptions(String capabilityId) {
        QueryOptions qops = new QueryOptions();
        final Filter capabilityIdFilter = Filter.eq("capabilities.id", capabilityId);

        if (this.excludeIndirectCapabilities) {
            //Just get capabilities that have a direct relation
            qops.add(capabilityIdFilter);
        } else {
            //Otherwise include identities that have indirect capabilities from a workgroup
            final Filter workgroupCapabilityIdFilter = Filter.eq("workgroups.capabilities.id", capabilityId);

            qops.add(Filter.or(capabilityIdFilter,
                               workgroupCapabilityIdFilter));
        }

        if (!this.excludeWorkgroups) {
            //include workgroup name in the list of identities
            qops.add(ObjectUtil.buildWorkgroupInclusiveIdentityFilter());
        }

        return qops;
    }

    /**
     * Builds the list of identity display names for a capability
     * @param it Iterator containing the identities for a capability
     * @param identityNames The List of identity names to return for a capability
     */
    private void buildNamesList(Iterator<Object[]> it, List<String> identityNames) {
        //build the List of names for this current Capability
        while (it != null && it.hasNext()) {
            Object[] row = (Object[]) it.next();
            String displayName = (String) row[0];

            if (Util.isNotNullOrEmpty(displayName)) {
                identityNames.add(displayName);
            }
        }
    }

    @Override
    public void setLimit(int startRow, int pageSize) {
     // preview is disabled so this is not necessary but required to extend Java datasource
    }

    @Override
    public int getSizeEstimate() throws GeneralException {
        return this.getContext().countObjects(Capability.class, this.ops);
    }

}
