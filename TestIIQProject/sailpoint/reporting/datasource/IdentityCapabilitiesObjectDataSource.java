/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * The Datasource behind the user to capabilities LiveReport
 */
public class IdentityCapabilitiesObjectDataSource extends ProjectionDataSource implements JavaDataSource {

    private static final Log log = LogFactory.getLog(IdentityCapabilitiesObjectDataSource.class);
    private static final String ARG_INCLUDE_EMPTY_CAPS = "includeEmptyCapabilities";
    private static final String ARG_EXCLUDE_WORKGROUPS = "excludeWorkgroups";
    private static final String ARG_EXCLUDE_INDIRECT_CAPS = "excludeIndirectCapabilities";
    private static final String PROP_NAME = "name";
    private static final String PROP_DISPLAY_NAME = "displayName";
    private static final String REPORT_COLUMNS_CAPS = "capabilities";

    private Iterator<Identity> identityIterator;
    private Identity currentIdentity;
    private List<Capability> currentCapabilities;
    private QueryOptions queryOptions;
    private SailPointContext context;
    private Locale locale;
    /**
     * Flag to skip identities that have no capabilities
     */
    private boolean includeEmptyCapabilities = false;
    private boolean excludeWorkgroups = false;
    private boolean excludeIndirectCapabilities = false;

    @Override
    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
            String groupBy, List<Sort> sort) throws GeneralException {
        this.context = getContext();
        TimeZone timeZone = (TimeZone) arguments.get(JRParameter.REPORT_TIME_ZONE);
        this.locale = (Locale) arguments.get(JRParameter.REPORT_LOCALE);
        this.includeEmptyCapabilities = arguments.getBoolean(ARG_INCLUDE_EMPTY_CAPS);
        this.excludeWorkgroups = arguments.getBoolean(ARG_EXCLUDE_WORKGROUPS);
        this.excludeIndirectCapabilities = arguments.getBoolean(ARG_EXCLUDE_INDIRECT_CAPS);

        ReportHelper helper = new ReportHelper(context, locale, timeZone);

        queryOptions = helper.getFilterQueryOps(report, arguments);
        if (!this.excludeWorkgroups) {
            queryOptions.add(ObjectUtil.buildWorkgroupInclusiveIdentityFilter());
        }
        //Use getContext to spawn new context for iterator
        identityIterator = this.context.search(Identity.class, queryOptions);
    }

    @Override
    public boolean next() throws JRException {
        while (identityIterator != null && identityIterator.hasNext()) {
            currentIdentity = identityIterator.next();

            currentCapabilities = this.excludeIndirectCapabilities ?
                                  currentIdentity.getCapabilities() :
                                  currentIdentity.getCapabilityManager().getEffectiveCapabilities();

            if (this.includeEmptyCapabilities) {
                //Always return true after we moved to the next identity when this flag is set.
                //This way every Identity is included in the report regardless of if they have capabilities or not.
                return true;
            }
            else if (!Util.isEmpty(currentCapabilities)) {
                //Otherwise only return true when there are capabilities to report
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getFieldValue(String field) throws GeneralException {
        if (field.equals(PROP_NAME)) {
            return currentIdentity.getName();
        } else if (field.equals(PROP_DISPLAY_NAME)) {
            return currentIdentity.getDisplayableName();
        } else if (field.equals(REPORT_COLUMNS_CAPS)) {

            return currentCapabilities != null ?
                   buildNamesList(currentCapabilities.stream()) :
                   null;
        }
        return null;
    }

    private List<String> buildNamesList(Stream<Capability> stream) {
        return stream.map(cap -> cap.getDisplayableName(this.locale))
                     //remove dupes from inherited capabilities
                     .distinct()
                     .collect(Collectors.toList());
    }

    @Override
    public Object getFieldValue(JRField arg0) throws JRException {
        return null;
    }

    @Override
    public int getSizeEstimate() throws GeneralException {
        return getContext().countObjects(Identity.class, queryOptions);
    }

    @Override
    public QueryOptions getBaseQueryOptions() {
        return queryOptions;
    }

    @Override
    public void setLimit(int startRow, int pageSize) {}
}
