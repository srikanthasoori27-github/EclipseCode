/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api.certification;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.MessageRenderer;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.timePeriod.TimePeriodUtil;
import sailpoint.object.Certification;
import sailpoint.object.Identity;
import sailpoint.api.CertificationContext;
import sailpoint.api.SailPointContext;
import sailpoint.web.messages.MessageKeys;

import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.List;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class CertificationNamer {

    public static final String NAME_TEMPLATE_TYPE = "type";
    public static final String NAME_TEMPLATE_DATE = "date";
    public static final String NAME_TEMPLATE_FULL_DATE = "fullDate";
    public static final String NAME_TEMPLATE_DATE_QUARTER = "quarter";
    public static final String NAME_TEMPLATE_DATE_MONTH = "month";
    public static final String NAME_TEMPLATE_DATE_YEAR = "year";
    public static final String NAME_TEMPLATE_CONTEXT = "certificationContext";
    public static final String NAME_TEMPLATE_CERTIFIER_PREFIX = "certifier";
    public static final String NAME_TEMPLATE_CERTIFIERS = "certifiers";

    public static final String NAME_TEMPLATE_ID_NAME_SUFFIX = "Name";
    public static final String NAME_TEMPLATE_ID_FULL_NAME_SUFFIX = "Fullname";
    public static final String NAME_TEMPLATE_ID_SUFFIX = "Identity";

    public static final String NAME_TEMPLATE_GLOBAL = "global";

    public static final String NAME_TEMPLATE_CERTIFIER_NAMES = "certifierNames";

    public static final String NAME_TEMPLATE_APP = "application";

    public static final String NAME_TEMPLATE_GROUP_OWNER = "groupOwner";
    public static final String NAME_TEMPLATE_FILTER_BY_OWNER = "filterByOwner";

    public static final String NAME_TEMPLATE_MANAGER_PREFIX = "manager";

    public static final String NAME_TEMPLATE_GROUP_NAME = "groupName";
    public static final String NAME_TEMPLATE_GROUP_FACTORY_NAME = "groupFactoryName";

    public static final String NAME_TEMPLATE_TARGET_ENTITY_PREFIX = "targetEntity";

    private Map<String, Object> params = new HashMap<String, Object>();
    
    private static final Log log = LogFactory.getLog(CertificationNamer.class);


    /**
     * Generic namer that for now handles naming of CertificationGroups. Thus it
     * doesn't include name parameters that are specific to a given certification
     * type. 
     */
    public CertificationNamer(SailPointContext spContext) {
        Date date = new Date();
        params.put(NAME_TEMPLATE_DATE, date);
        params.put(NAME_TEMPLATE_FULL_DATE, Internationalizer.getLocalizedDate(date,
                Internationalizer.IIQ_DEFAULT_DATE_STYLE, DateFormat.LONG, Locale.getDefault(),
                TimeZone.getDefault()));

        int quarter = -1;
        try {
            quarter = TimePeriodUtil.getQuarter(date, spContext);
            if (quarter == -1) {
                //Better configure your quarters right, or you might get -1 in your name
                log.warn("Date does not fall in any configured quarter time period");
            }
        }
        catch (GeneralException ge) {
            //log it and swallow exception, its only a name.
            log.warn("Could not get quarter for CertificationNamer", ge);
        }

        params.put(NAME_TEMPLATE_DATE_QUARTER, Message.info(MessageKeys.QUARTER_FORMAT, quarter).getLocalizedMessage());
        params.put(NAME_TEMPLATE_DATE_MONTH, new SimpleDateFormat("MMMM",Locale.getDefault()).format(date));
        params.put(NAME_TEMPLATE_DATE_YEAR, new SimpleDateFormat("yyyy",Locale.getDefault()).format(date));

    }

    /**
     * Creates a namer instance for naming Access Reviews created by a given
     * CertificationContext.
     * @param context
     * @param owners
     */
    public CertificationNamer(CertificationContext context, List<Identity> owners, SailPointContext spContext) {
        this(spContext);
        params.put(NAME_TEMPLATE_CONTEXT, context);
        params.put(CertificationNamer.NAME_TEMPLATE_TYPE, context.getType());
        addOwners(owners);
    }

    public String render(String template) throws GeneralException {
        return MessageRenderer.render(template, params);
    }

    public void addParameter(String key, Object val) {
        params.put(key, val);
    }

    public void addParameters(Map<String, Object> newParams) {
        if (newParams != null)
            params.putAll(newParams);
    }

    public void addOwners(List<Identity> owners) {
        if (null != owners) {
            StringBuilder names = new StringBuilder();
            StringBuilder fullnames = new StringBuilder();
            String sep = "";

            for (Identity id : owners) {
                String fullname = id.getFullName();
                if (null == Util.getString(fullname)) {
                    fullname = id.getName();
                }

                names.append(sep).append(id.getName());
                fullnames.append(sep).append(fullname);
                sep = ", ";
            }

            // Chop these off at 50 characters.
            final int MAX_LENGTH = 50;
            params.put(NAME_TEMPLATE_CERTIFIER_PREFIX + NAME_TEMPLATE_ID_NAME_SUFFIX,
                    Util.truncate(names.toString(), MAX_LENGTH));
            params.put(NAME_TEMPLATE_CERTIFIER_PREFIX + NAME_TEMPLATE_ID_FULL_NAME_SUFFIX,
                    Util.truncate(fullnames.toString(), MAX_LENGTH));
            params.put(NAME_TEMPLATE_CERTIFIERS, owners);
        }
    }

    public void addIdentity(Identity identity, String paramPrefix) {
        if (null != identity) {
            // Default to the name if the identity doesn't have a full name.
            // This will prevent non-sensical certification names.
            String fullname = identity.getFullName();
            if (null == Util.getString(fullname)) {
                fullname = identity.getName();
            }

            params.put(paramPrefix + NAME_TEMPLATE_ID_NAME_SUFFIX, identity.getName());
            params.put(paramPrefix + NAME_TEMPLATE_ID_FULL_NAME_SUFFIX, fullname);
            params.put(paramPrefix + NAME_TEMPLATE_ID_SUFFIX, identity);
        }
    }

    public static String getDefaultNameTemplate(Certification.Type type) {
        return "";
    }

}
