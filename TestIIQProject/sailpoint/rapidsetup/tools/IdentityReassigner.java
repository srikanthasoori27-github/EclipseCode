/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class IdentityReassigner extends BaseReassigner {

    private static Log log = LogFactory.getLog(IdentityReassigner.class);

    /**
     * the name of the process (for debugging).  Expected to be
     * "leaver" or "terminate"
     */
    private String process;

    /**
     *
     * @param reassignIdentitiesConfig the reassignment configuration settings
     * @param process the name of the process (for debugging).  Expected to be
     *                "leaver" or "terminate"
     */
    public IdentityReassigner(Map reassignIdentitiesConfig, String process) {
        super(reassignIdentitiesConfig);
        this.process = process;
    }

    @Override
    public String getClassNameKey(Class clazz) {
        return "Identity Administrator";
    }

    public ReassignResult reassignIdentities(SailPointContext context, String identityName)
            throws GeneralException {
        if (Util.isEmpty(getProcessConfig())) {
            // nothing to do
            log.debug("No " + process + " configuration found.  Skipping reassignments.");
            return EMPTY;
        }

        if (!isReassignmentEnabled()) {
            log.debug("Reassign is disabled");
            return EMPTY;
        }

        Identity identity = context.getObjectByName(Identity.class, identityName);
        if (identity == null) {
            log.debug("Unknown identity " + identityName);
            return EMPTY;
        }

        Identity newOwner = calculateNewOwner(context, identity, getProcessConfig());
        log.debug("The new administrator for identities will be " + ((newOwner != null) ? newOwner.getName() : "null") );

        //Identity Administrators
        Map<String, List<String>> ownershipMap = new HashMap<>();

        if (isReassignmentEnabled()) {
            List<String> typesToReassign = getTypesToReassign(context);

            //This is for notification, we want send notification to right owner
            QueryOptions queryOptions = new QueryOptions();
            Filter identityTypeFilter = Filter.in("type", typesToReassign);
            Filter adminNameFilter = Filter.eq("administrator.name", identityName);
            queryOptions.addFilter(identityTypeFilter);
            queryOptions.addFilter(adminNameFilter);

            //Get list of Cube Names for each attr
            List<String> identityCubeIds = getIdentityCubeIds(queryOptions, context);
            if (Util.isEmpty(identityCubeIds)) {
                log.debug("No identities found which are administered by " + identityName);
            }
            doReassignment(context, Identity.class, newOwner, identityCubeIds, ownershipMap, (spo, owner) -> ((Identity)spo).setAdministrator(owner));
        }

        String newOwnerName = null;
        if(newOwner != null) {
            newOwnerName = newOwner.getName();
            context.decache(newOwner);
        }

        ReassignResult result = new ReassignResult(newOwnerName, ownershipMap);

        if(newOwner != null) {
            context.decache(newOwner);
        }
        context.decache(identity);

        log.debug("End reassignIdentityAdministrator" );
        return result;
    }

    /**
     * Get List of Cube Names that owns Identities
     * @param queryOptions
     * @return
     */
    public static List getIdentityCubeIds(QueryOptions queryOptions, SailPointContext context) throws GeneralException {
        List cubeNames= new ArrayList();
        // Use a projection query first to return minimal data.
        ArrayList returnCols = new ArrayList();
        returnCols.add("id");
        String identityName;
        // Execute the query against the IdentityIQ database.
        Iterator it = context.search(Identity.class, queryOptions, returnCols);
        if(it != null) {
            while (it.hasNext()) {
                Object[] retObjs = (Object[]) it.next();
                if(retObjs != null && retObjs.length == 1) {
                    identityName   = (String) retObjs[0];
                    if(identityName != null) {
                        cubeNames.add(identityName);
                    }
                }
            }
            Util.flushIterator(it);
        }
        return cubeNames;
    }

    private List<String> getTypesToReassign(SailPointContext ctx) {
        List<String> typesToReassign = new ArrayList<String>();

        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        Map<String, IdentityTypeDefinition> typeDefinitionMap = identityConfig.getIdentityTypesMap();
        for(IdentityTypeDefinition typeDefinition : typeDefinitionMap.values()) {
            List<String> disallowedAttributes = typeDefinition.getDisallowedAttributes();
            if((disallowedAttributes != null) && (!disallowedAttributes.contains(Identity.ATT_ADMINISTRATOR))) {
                typesToReassign.add(typeDefinition.getName());
            }
        }

        return typesToReassign;
    }

}
