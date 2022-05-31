/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

abstract public class BaseReassigner {
    private static Log log = LogFactory.getLog(BaseReassigner.class);

    private final Map processConfig;

    protected static ReassignResult EMPTY = new ReassignResult(null, Collections.emptyMap());

    public static String reassignmentEnabledKey = "enableReassignment";
    public static String reassignToManagerKey = "reassignToManager";
    public static String reassignmentRuleKey = "reassignRule";
    public static String reassignAlternateKey = "reassignAlternative";

    public interface Reassignment {
        void reassign(SailPointObject spo, Identity owner);
    }

    BaseReassigner(Map processConfig) {
        this.processConfig = processConfig;
    }

    protected Map getProcessConfig() {
        return processConfig;
    }

    public String getClassNameKey(Class clazz) {
        return clazz.getSimpleName() + " Owner";
    }

    protected boolean isReassignmentEnabled() {
        return Util.otob(processConfig.get(reassignmentEnabledKey));
    }

    public boolean isReassignToManager() {
        return Util.otob(processConfig.get(reassignToManagerKey));
    }

    public Rule getReassignmentRule(SailPointContext ctx) throws GeneralException {
        Rule rule = null;

        String ruleName = Util.otos(processConfig.get(reassignmentRuleKey));
        if(Util.isNotNullOrEmpty(ruleName)) {
            rule = ctx.getObjectByName(Rule.class, ruleName);
        }

        return rule;
    }

    public Identity getReassignAlternate(SailPointContext ctx) throws GeneralException {
        return getIdentityFromObject(ctx, processConfig.get(reassignAlternateKey), "AltOwner");
    }

    /**
     * Return the identity named by the given object.  The object can be a string or a map with a "name" key.
     * @param context persistence context
     * @param obj the Object from which to find the identity.  Should be a String or a Map with "name" key.
     * @param purpose used for logging when the object refers to an unfound identity
     * @return the identity referred to by the obj
     * @throws GeneralException unexpected database error
     */
    protected Identity getIdentityFromObject(SailPointContext context, Object obj, String purpose) throws GeneralException {
        Identity identity = null;

        if (obj != null) {
            String identityName = (String)obj;
            identity = context.getObjectByName(Identity.class, identityName);
            if (identity == null) {
                log.debug("Cannot find identity " + identityName + ".  No value set for " + purpose);
            }
        }

        return identity;
    }

    /**
     * Invoke the given Rule, expecting an identity name to be returned
     * @param context persistence context (passed to rule)
     * @param identityName the identityName (of the leaving identity) to pass to rule
     * @param rule the rule to execute
     * @return the identity name returned by rule
     * @throws GeneralException if rule doesn't exist, or rule throws an exception
     */
    protected String invokeReassignmentRule(SailPointContext context, String identityName, Rule rule)
            throws GeneralException
    {
        String ruleName = rule.getName();
        log.debug("Enter invokeReassignmentRule " + ruleName);

        try {
            Map<String,Object> params = new HashMap<>();
            params.put("context", context);
            params.put("identityName", identityName);
            try {
                log.debug("...Run the rule");
                String assignTo = (String)context.runRule(rule, params);

                log.debug("Exit invokeReassignmentRule " + ruleName + " : returned " + assignTo);
                return assignTo;
            }
            catch (Exception re) {
                throw new GeneralException("Error executing rule : " + re.getMessage());
            }
        }
        finally {
            if(rule != null) {
                context.decache(rule);
            }
        }
    }

    /**
     * Calculates the new owner of an object being reassigned based on current owner's manager,
     * a rule, or an alternate explicit identity.
     *
     * @param ctx sailpoint context
     * @param identity the identity of the current owner
     * @param processConfig the config object holding the configuration information
     * @return the new owner's identity
     * @throws GeneralException
     */
    protected Identity calculateNewOwner(SailPointContext ctx, Identity identity, Map processConfig) throws GeneralException {
        // Compute who the new owner should be
        Identity newOwner = null;

        // First, use the leaving identity's manager -- if the reassignToManger is specified and the leaving
        // identity actually has a manager
        if (isReassignToManager()) {
            // first, try the leaving identity's manager
            Identity currentMgr = identity.getManager();
            if (currentMgr != null) {
                log.debug("Found Manager On Identity");
                newOwner = currentMgr;
            } else {
                log.debug("Identity has no manager, attempting to evaluate rule instead.");
            }
        }
        else {
            log.debug("Reassignment to manager is disabled, attempting to evaluate rule instead.");
        }

        if (newOwner == null) {
            // next, try the rule
            Rule reassignRule = getReassignmentRule(ctx);
            if (reassignRule != null) {
                log.debug("Evaluating reassignment rule " + reassignRule.getName() + " to determine owner name");
                String newOwnerName = invokeReassignmentRule(ctx, identity.getName(), reassignRule);
                if (Util.isNotNullOrEmpty(newOwnerName)) {
                    newOwner = ctx.getObjectByName(Identity.class, newOwnerName);
                    if (newOwner != null) {
                        log.debug("Found owner from rule");
                    } else {
                        log.debug("Owner " + newOwnerName + " from rule was not found. Defaulting to alternative owner");
                    }
                }
                else {
                    log.debug("No owner name was returned from rule. Defaulting to alternative owner");
                }
            }
            else {
                log.debug("No reassignment rule found.  Defaulting to alternative owner");
            }
        }

        if (newOwner == null) {
            // Nothing else provided us a new owner, let's try the alternative owner
            newOwner = getReassignAlternate(ctx);
            log.debug("Defaulting to alternative owner " +  ((newOwner != null) ? newOwner.getName() : "null"));
        }
        return newOwner;
    }

    protected void doReassignment(SailPointContext ctx, Class clazz, Identity newOwner, List<String> ownedItemIds,
                                  Map<String,List<String>> ownershipMap, Reassignment reassignment) throws GeneralException {
        int count = 0;
        final int commitLimit = 100;
        List<SailPointObject> decacheObjects = new ArrayList<>();
        String lockMode = PersistenceManager.LOCK_TYPE_TRANSACTION;

        try {
            for (String ownedItemId : ownedItemIds) {
                log.debug("Locking object (type=" + clazz.getSimpleName() + ", id=" + ownedItemId + ")");
                SailPointObject spo = null;
                String classNameKey = getClassNameKey(clazz);

                try {
                spo = ObjectUtil.lockObject(ctx, clazz, ownedItemId, null, lockMode);
                } catch (Exception e) {
                    List<String> list = ownershipMap.computeIfAbsent(classNameKey, key -> new ArrayList<>());
                    StringBuilder ownershipMapMessage = new StringBuilder();
                    ownershipMapMessage.append(getOwnershipMapString(clazz, ownedItemId, spo));
                    ownershipMapMessage.append(", ");
                    ownershipMapMessage.append(
                            new Message(MessageKeys.ERR_LOCKED_UNABLE_TO_REASSIGN).getLocalizedMessage());
                    list.add(ownershipMapMessage.toString());
                    log.warn("Unable to lock object of type " + clazz.getSimpleName()
                            + " with id " + ownedItemId);
                }

                if (spo == null) {
                    log.warn("Unable to read object of type " + clazz.getSimpleName()
                            + " with id " + ownedItemId + " - skipping");
                    continue;
                }

                if (newOwner != null) {
                    // Here is where we finally perform the actual owner change
                    // on the artifact
                    reassignment.reassign(spo, newOwner);
                    ctx.saveObject(spo);
                    log.debug("Changing " + classNameKey + " with name='" + spo.getName() + "'  id='" + spo.getId() + "' to " + newOwner.getName());
                }
                decacheObjects.add(spo);

                log.debug("...ownershipMap=" + ownershipMap);

                List<String> currList = ownershipMap.computeIfAbsent(classNameKey, key -> new ArrayList<>());
                currList.add(getOwnershipMapString(clazz, ownedItemId, spo));

                count++;
                if ((count % commitLimit) == 0) {
                    ctx.commitTransaction();
                }
            }

            ctx.commitTransaction();

            // Decache all Objects that are collected for IdentityIQ Artifacts
            for (SailPointObject spo : Util.safeIterable(decacheObjects))  {
                ctx.decache(spo);
            }

        } finally {
            //Final Commit
            ctx.commitTransaction();
        }
    }

    private String getOwnershipMapString(Class clazz, String id, SailPointObject spo) {
        if (clazz == ManagedAttribute.class) {
            ManagedAttribute ma = (ManagedAttribute) spo;
            if (ma.getDisplayName() != null)
                return ma.getDisplayName();
            else
                return ma.getValue();
        }
        else if ((spo != null) && (spo.getName() != null)) {
            return spo.getName();
        }

        // if we can't identify the object an any other way, return the id
        return id;
    }
}
