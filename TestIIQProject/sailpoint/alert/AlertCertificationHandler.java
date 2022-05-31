/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationTriggerHandler;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;

import sailpoint.object.Alert;
import sailpoint.object.AlertAction;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Certification;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Reflection;

import java.util.Date;

/**
 * Created by ryan.pickens on 7/5/16.
 */
public class AlertCertificationHandler implements AlertHandler {

    Log _log = LogFactory.getLog(AlertCertificationHandler.class);

    @Override
    public AlertAction handleAlert(Alert a, AlertDefinition def, SailPointContext ctx)
        throws GeneralException {

        if (a == null) {
            _log.error("No Alert found");
            return null;
        } else {
            a.load();
        }

        if (def == null) {
            _log.error("No Alert Definition");
            return null;
        } else {
            def.load();
        }

        if (ctx == null) {
            _log.error("Null Context");
            return null;
        }

        AlertAction action = null;

        try {
            if (Util.nullSafeEq(a.getTargetType(), Identity.class.getSimpleName())) {
                AlertService svc = new AlertService(ctx);
                Identity ident = (Identity) svc.getTargetObject(a);
                if (ident == null) {
                    _log.error("No Identity found for alert " + a.getName());
                    throw new GeneralException("No Identity Found to certify for Alert[" + a.getName() + "] " +
                            "AlertDefinition[" + def.getName()+ "]");
                } else {

                    String certId = null;
                    IdentityTrigger trigger = getCertificationTrigger(def, ctx);
                    if (trigger == null) {
                        _log.error("No Certification Trigger found for alertDefinition[" + def.getName() + "]");
                        throw new GeneralException("No Certification Trigger found for alertDefinition[" + def.getName() + "]");
                    }

                    String handlerClass = trigger.getHandler();

                    if (Util.isNotNullOrEmpty(handlerClass)) {
                        CertificationTriggerHandler handler =
                                (CertificationTriggerHandler) Reflection.newInstance(handlerClass);
                        handler.setContext(ctx);
                        certId = handler.handleAlertEvent(trigger, ident);
                    } else {
                        _log.error("No Handler fround for Trigger[" + trigger.getName() + "]");
                        throw new GeneralException("No Handler fround for Trigger[" + trigger.getName() + "]");
                    }

                    //Cert generation runs decache. Reattach objects
                    if (a.getId() != null) {
                        //If Alert already persisted,
                        //Reattach the Alert in case of Cert generation decache
                        a = ObjectUtil.reattach(ctx, a);
                    }
                    if (def.getId() != null) {
                        //Reattach alertDef if persisted
                        def = ObjectUtil.reattach(ctx, def);
                    }

                    if (Util.isNotNullOrEmpty(certId)) {

                        //Create the Alert Action
                        action = new AlertAction();
                        action.setAlertDef(def);
                        action.setActionType(AlertDefinition.ActionType.CERTIFICATION);
                        //Get Certification name to store in result
                        Certification cert = ctx.getObjectById(Certification.class, certId);
                        AlertAction.AlertActionResult res = new AlertAction.AlertActionResult();
                        res.setResultId(certId);
                        res.setResultName(cert != null ? cert.getName() : null);

                        action.setResult(res);
                        action.setResultId(certId);


                        //Update the Alert
                        a.addAction(action);
                        a.setLastProcessed(new Date());

                        ctx.saveObject(a);
                        ctx.commitTransaction();

                        AlertService.auditAlertAction(a, action, ctx);
                    } else {
                        _log.error("No Certification generated for alert[ " + a.getId()
                        + " ]AlertDefinition [" + def.getName() + " ]");
                    }

                }


            } else {
                if (_log.isInfoEnabled()) {
                    _log.info("Matched Alert " + a.getName() + " to AlertDef " + def.getName() + " but no identity" +
                            " set on alert. Skipping.");
                }
            }
        } catch (GeneralException ge) {
            _log.error("Error creating Certification for alert " + a.getName(), ge);
            throw ge;
        }


        return action;
    }


    private IdentityTrigger getCertificationTrigger(AlertDefinition def, SailPointContext ctx) throws GeneralException {
        IdentityTrigger trigger = null;
        AlertDefinition.ActionConfig actCnf = def.getActionConfig();
        if (actCnf != null) {
            String certTrigger = actCnf.getStringAttributeValue(AlertDefinition.ActionConfig.ARG_CERT_TRIGGER);
            if (Util.isNotNullOrEmpty(certTrigger)) {
                trigger = ctx.getObjectByName(IdentityTrigger.class, certTrigger);
            } else {
                _log.error("No Certification Trigger found for AlertDefinition " + def.getName());
            }
        } else {
            _log.error("No Action Config for AlertDefintiion " + def.getName());
        }

        return trigger;
    }
}
