/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Correlator;
import sailpoint.api.SailPointContext;
import sailpoint.fam.model.FAMObject;
import sailpoint.fam.model.IdentityGroup;
import sailpoint.fam.model.IdentityUser;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to aid in computing FAM correlation keys.
 *
 * Can make assumptions as to which attribute(s) are used for correlation, or allow the app
 * to store some configuration
 */
public class CorrelationHelper {

    private static Log _log = LogFactory.getLog(CorrelationHelper.class);


    public static final String APP_AD_DIRECT = "Active Directory - Direct";
    public static String ATT_OBJECT_SID = "objectSid";
    public static final String APP_WINDOWS_LOCAL_DIRECT = "Windows Local - Direct";
    public static String ATT_SAM_ACCNT_NAME = "sAMAccountName";
    public static final String APP_AZURE_AD = "Azure Active Directory";
    public static String ATT_OBJECT_ID = "objectId";

    //FAM supports AD, Azure AD, NIS && Datasource (Not sure we have any idea of datasource)

    //AD - returns objectSID
    //Azure AD - returns objectId
    //Local groups - (at least for windows local) returns msdsPrincipalName

    //ADAM-Direct?

    //ADDirect
    //msDS-PrincipalName - used for both users and groups
    //ADLDAPConnector.ATTR_MSDS_PRINCIPAL_NAME

    public static String getCorrelationId(SailPointObject o) {
        String correlationKey = null;
        if (o != null) {
            if (o instanceof ManagedAttribute) {
                ManagedAttribute att = (ManagedAttribute) o;
                if (att.getApplication() != null) {
                    if (Util.isNotNullOrEmpty(att.getApplication().getType())) {
                        switch (att.getApplication().getType()) {
                            case APP_AD_DIRECT:
                                correlationKey = Util.otos(att.get(ATT_OBJECT_SID));
                                break;
                            case APP_AZURE_AD:
                                //Not sure what to do for Azure groups atm -rap
                                //Should be objectId
                                correlationKey = Util.otos(att.get(ATT_OBJECT_ID));
                                break;
                            case APP_WINDOWS_LOCAL_DIRECT:
                                //domain\\username -- assume SAMAccountName? -rap
                                correlationKey = Util.otos(att.get(ATT_SAM_ACCNT_NAME));

                            default:
                                _log.warn("Unknown Application type: " + att.getApplication().getType());
                                break;
                        }
                    } else {
                        _log.warn("No type associated to Application " + att.getApplication().getName());
                    }
                } else {
                    _log.error("No Application associated to ManagedAttribute[" + att +"]");
                }

            } else if (o instanceof Link) {
                Link l = (Link)o;
                if (l.getApplication() != null) {
                    switch (l.getApplication().getType()) {
                        case APP_AD_DIRECT:
                            correlationKey = Util.otos(l.getAttribute(ATT_OBJECT_SID));
                            break;
                        case APP_AZURE_AD:
                            correlationKey = Util.otos(l.getAttribute(ATT_OBJECT_ID));
                            break;
                        case APP_WINDOWS_LOCAL_DIRECT:
                            //domain\\username -- assume SAMAccountName? -rap
                            correlationKey = Util.otos(l.getAttribute(ATT_SAM_ACCNT_NAME));

                        default:
                            _log.warn("Unknown Application type: " + l.getApplication().getType());
                            break;
                    }
                } else {
                    _log.error("No Application associated to Link[" + l +"]");
                }

            } else {
                //Unknown object Type
                _log.error("Unknown object type. Must be ManagedAttribute or Link");
            }
        }

        //TODO: Allow rule hook. Same rule going both ways? -rap
        //What is this rule called if not?


        return correlationKey;
    }

    public static SailPointObject getSailPointObject(FAMObject obj, SailPointContext ctx,
                                                     Map<String, SailPointObject> correlationCache)
            throws GeneralException {

        SailPointObject spObject = null;

        if (obj != null) {

            if (correlationCache != null && correlationCache.containsKey(getCacheKey(obj))) {
                spObject = correlationCache.get(getCacheKey(obj));
            } else {
                Configuration cfg = Configuration.getFAMConfig();
                String ruleName = cfg.getString(Configuration.FAM_CONFIG_SCIM_CORRELATION_RULE);
                if (Util.isNotNullOrEmpty(ruleName)) {
                    Rule correlationRule = ctx.getObjectByName(Rule.class, ruleName);
                    if (correlationRule != null) {

                        spObject = runCorrelationRule(obj, correlationRule, ctx);
                        //TODO: Cache these as well? Assume rule will awlays return same result for same FAMObject?
                    } else {
                        if (_log.isWarnEnabled()) {
                            _log.warn("Could not find rule with name[ " + ruleName + " ]");
                        }
                    }
                } else {
                    if (_log.isInfoEnabled()) {
                        _log.info("No FAM Correlation Rule Defined");
                    }
                }

                if (spObject == null) {

                    if (obj instanceof IdentityUser) {
                        String identifier = ((IdentityUser) obj).getUniqueIdentifier();
                        spObject = correlateInternal(Link.class, identifier, ctx);

                    } else if (obj instanceof IdentityGroup) {
                        String identifier = ((IdentityGroup) obj).getUniqueIdentifier();
                        if (Util.isNotNullOrEmpty(identifier)) {
                            spObject = correlateInternal(ManagedAttribute.class, identifier, ctx);
                        }

                    } else {
                        _log.warn("Unknown type of FAMObject to correlate" + obj);
                    }
                }

                if (correlationCache != null && spObject != null) {
                    correlationCache.put(getCacheKey(obj), spObject);
                }
            }
        } else {
            _log.warn("FAMObject must be non-null");
        }

        return spObject;
    }

    static String USER_PREFIX = "user";
    static String GROUP_PREFIX = "group";

    static String getCacheKey(FAMObject obj) {
        String key = "";
        if (obj instanceof IdentityGroup) {
            key += GROUP_PREFIX;
            key += ((IdentityGroup) obj).getUniqueIdentifier();
        } else if (obj instanceof  IdentityUser) {
            key += USER_PREFIX;
            key += ((IdentityUser) obj).getUniqueIdentifier();
        }

        return key;
    }

    public static <T extends SailPointObject> SailPointObject correlateInternal(Class<T> spClass, String identifier, SailPointContext ctx)
        throws GeneralException {
        Configuration cfg = Configuration.getFAMConfig();

        List<String> apps = null;
        if (cfg != null) {
            apps = Util.otol(cfg.get(Configuration.FAM_CONFIG_SCIM_CORRELATION_APPS));

        } else {
            _log.warn("No FAM Configuration Found");
            return null;
        }

        if (!Util.isEmpty(apps)) {
            for (String appName : Util.safeIterable(apps)) {
                //See if it's found here
                Application appl = ctx.getObjectByName(Application.class, appName);
                if ( appl != null) {
                    //Look at all group schemas? this will likely always be group
                    List<Schema> schemas = null;
                    if (spClass == Link.class) {
                        if (appl.getAccountSchema() != null) {
                            schemas = Arrays.asList(appl.getAccountSchema());
                        }
                    } else {
                        schemas = appl.getGroupSchemas();
                    }

                    for (Schema sch : Util.safeIterable(schemas)) {
                        List<String> correlationKeys = new ArrayList<>();
                        for (AttributeDefinition attDef : Util.safeIterable(sch.getAttributes())) {
                            if (attDef.getCorrelationKey() > 0) {
                                correlationKeys.add("key"+attDef.getCorrelationKey());
                            }
                        }

                        //If we found at least one correlationKey, issue query
                        if (!Util.isEmpty(correlationKeys)) {
                            QueryOptions ops = new QueryOptions();
                            List<Filter> keyFilters = new ArrayList();
                            for (String key : Util.safeIterable(correlationKeys)) {
                                keyFilters.add(Filter.eq(key, identifier));
                            }
                            ops.add(Filter.or(keyFilters));
                            ops.add(Filter.eq("application", appl));
                            //TODO: May need to look at value as well? -rap
                            //Need instance in case of link? -rap
                            List<T> spObjs = ctx.getObjects(spClass, ops);
                            if (spObjs.size() > 1) {
                                _log.error("Found Multiple objects matching identifier" + identifier);
                                return null;
                            }

                            if (spObjs.size() == 1) {
                                return (T)spObjs.get(0);
                            }
                            //Else try next schema/app
                        }

                    }
                }

            }

        } else {
            //Use all apps or log warning?
            _log.warn("No Correlation Applications specified in FAM Config");
        }

        return null;
    }

    public static SailPointObject runCorrelationRule(FAMObject obj, Rule r, SailPointContext ctx) throws GeneralException {
        Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put("famObject", obj);
        Configuration cfg = Configuration.getFAMConfig();
        List<Application> apps = null;
        if (cfg != null) {
            List<String> appNames = Util.otol(cfg.get(Configuration.FAM_CONFIG_SCIM_CORRELATION_APPS));
            if (!Util.isEmpty(appNames)) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.in("name", appNames));
                apps = ctx.getObjects(Application.class, ops);
            }
        }
        Correlator correlator = new Correlator(ctx);
        return correlator.runTargetCorrelationRule(apps, inputs, r);
    }

}
