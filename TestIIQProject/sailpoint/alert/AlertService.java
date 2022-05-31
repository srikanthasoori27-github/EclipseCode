/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;

import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.AlertAction;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Application;
import sailpoint.object.AttributeSource;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.server.Auditor;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Service Class to handle Alerts
 *
 * Created by ryan.pickens on 6/22/16.
 */
public class AlertService {

    private static Log _log = LogFactory.getLog(AlertService.class);

    SailPointContext _context;

    public AlertService(SailPointContext ctx) {
        _context = ctx;
    }
    
    public List<AlertDefinition> getAlertDefinitions(List<String> alertDefNames) throws GeneralException {
        List<AlertDefinition> defs = new ArrayList<>();

        QueryOptions ops = new QueryOptions();

        if (!Util.isEmpty(alertDefNames)) {
            //If arg present, filter alert defs
            ops.add(Filter.in("name", alertDefNames));
        }

        defs = _context.getObjects(AlertDefinition.class, ops);

        return defs;
    }
    
    public void correlateAlert(Alert a, Application appSource) throws GeneralException {
        if (appSource == null) {
            return;
        }
        correlateAlert(a, appSource, appSource.getCorrelationRule(Schema.TYPE_ALERT));
    }
    
    /**
     * If CorrelationRule specified, try and correlate the alert to a SailPointObject.
     * The correlationRule must return a SailPointObject. If correlated, the alert targetType/targetId will be populated.
     * TargetType will be populated with the SailPointObject class simplename
     * TargetId will be the SailPointObject id
     * @param a the Alert
     * @param appSource the Application source to pass to the rule
     * @param correlationRule the rule to run
     * @throws GeneralException
     */
    public void correlateAlert(Alert a, Application appSource, Rule correlationRule) throws GeneralException {
        // appSource can be null here, Rules need to consider null sources in 8.1
        if (correlationRule != null && a != null) {
            Map<String,Object> ruleOps = new HashMap<>();
            ruleOps.put("alert", a);
            ruleOps.put("source", appSource);
            Object obj = _context.runRule(correlationRule, ruleOps);
            if (obj != null) {
                if (obj instanceof SailPointObject) {
                    a.setTargetType(Hibernate.getClass(obj).getSimpleName());
                    a.setTargetId(((SailPointObject) obj).getId());
                    a.setTargetDisplayName(((SailPointObject) obj).getName());
                } else {
                    _log.error("Correlation Rule must return a SailPointObject");
                }
            } else {
                _log.debug("Could not correlate alert[" + a.toXml() + "]");
            }
        }
    }

    /**
     * Runs the creation rule in the given request
     * @param a the Alert
     * @param appSource Application passed to the rule as the 'source' argument
     * @param creationRule the Rule itself
     * @return the newly created Alert, or null if the Alert shouldn't be created
     * @throws GeneralException
     */
    public Alert runCreationRule(Alert a, Application appSource, Rule creationRule) throws GeneralException {
        Alert updatedAlert = null;
        // appSource can be null here, Rules need to consider null sources in 8.1
        if (a != null) {
            if (creationRule != null) {
                Map<String, Object> ruleOps = new HashMap<>();
                ruleOps.put("alert", a);
                ruleOps.put("source", appSource);
                Object obj = _context.runRule(creationRule, ruleOps);
                if (obj == null) {
                    //This Alert will be ignored
                    _log.info("Creation rule returned null, ignore Alert: " + a.toString());
                } else {
                    if (obj instanceof Alert) {
                        updatedAlert = (Alert)obj;
                    } else {
                        _log.error("Creation Rule must return an Alert object. Using Alert prior to creation rule");
                        updatedAlert = a;
                    }
                }
            } else {
                updatedAlert = a;
            }
        }
        return updatedAlert;
    }
    
    public static final String RULE_ARG_ALERT = "alert";
    public static final String RULE_ARG_OBJ_ATT = "objectAttribute";
    public static final String RULE_ARG_ATT_SRC = "attributeSource";

    //TODO: We currently don't support editing through UI. Therefore, all extended attributes
    // need to be sourced or set in the connector
    public void promoteAttributes(Alert t) throws GeneralException {
        Attributes<String,Object> newatts = t.getAttributes();
        if (newatts == null) {
            // shouldn't happen but we must have a target map for
            // transfer of existing system attributes
            newatts = new Attributes<>();
        }

        //extended attributes
        ObjectConfig config = Alert.getObjectConfig();
        if (config != null) {
            List<ObjectAttribute> extended = config.getObjectAttributes();
            for (ObjectAttribute ext : Util.safeIterable(extended)) {

                List<AttributeSource> sources = ext.getSources();
                if (!Util.isEmpty(sources)) {
                    int max = (sources != null) ? sources.size() : 0;
                    for (int i = 0 ; i < max ; i++) {
                        AttributeSource src = sources.get(i);
                        Object val = null;
                        if (Util.isNotNullOrEmpty(src.getName())) {
                            val = t.getAttribute(src.getName());

                        } else if (src.getRule() != null){
                            Rule rule = src.getRule();
                            Map<String,Object> args = new HashMap<>();
                            args.put(RULE_ARG_ALERT, t);
                            args.put(RULE_ARG_ATT_SRC, src);
                            args.put(RULE_ARG_OBJ_ATT, ext);
                            val = _context.runRule(rule, args);

                        } else {
                            if (_log.isWarnEnabled())
                                _log.warn("AttributeSource with nothing to do: " +
                                        src.getName());
                        }
                        if (val != null) {
                            newatts.put(ext.getName(), val);
                            break;
                        }
                    }
                }
            }
        }

        t.setAttributes(newatts);

    }

    private static final String TARGET_PACKAGE = "sailpoint.object.";

    /**
     * Return the SailPointObject associated to a given alert.
     * @param a the Alert
     * @return object associated with the Alert
     * @throws GeneralException
     */
    public SailPointObject getTargetObject(Alert a)  throws GeneralException {

        SailPointObject obj = null;
        if (a != null) {
            if (a.getTargetType() != null && a.getTargetId() != null) {
                Class c = null;
                try {
                    c = Class.forName(TARGET_PACKAGE + a.getTargetType());
                } catch (ClassNotFoundException cnfe) {
                    _log.warn("Could not find class " + a.getTargetType());
                    return null;
                }

                if (c != null) {
                    obj = _context.getObjectById(c, a.getTargetId());
                }
            }
        }

        return obj;
    }

    public static String AUDIT_EVENT_ACTION_ATTRIBUTE = "action";
    static void audit(String auditEvent, String target, Attributes<String, Object> atts, SailPointContext context)
            throws GeneralException {
        if (Auditor.isEnabled(auditEvent)) {
            AuditEvent event = new AuditEvent();
            event.setAction(auditEvent);
            event.setApplication(BrandingServiceFactory.getService().getApplicationName());
            event.setTarget(target);
            event.setAttributes(atts);
            Auditor.log(event);
            context.commitTransaction();

        }
    }

    static void auditAlertAction(Alert a, AlertAction action, SailPointContext context)
            throws GeneralException {
        if (a != null && action != null) {
            Attributes<String, Object> atts = new Attributes<>();
            atts.put(AUDIT_EVENT_ACTION_ATTRIBUTE, action);
            audit(AuditEvent.AlertActionCreated, a.getName(), atts, context);
        }
    }

    static void auditAlertProcessing(Alert a, SailPointContext context)
            throws GeneralException {
        if (a != null) {
            audit(AuditEvent.AlertProcessed, a.getName(), null, context);

        }
    }
}
