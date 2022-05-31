/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.ClassLists;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * Use an instance of Reassigner to reassign the owner of objects
 * away from the current owner, usually because the current owner is leaving
 */
public class Reassigner extends BaseReassigner {

    private static Log log = LogFactory.getLog(Reassigner.class);

    public static String CFG_KEY_REASSIGN_TYPES = "reassignArtifactTypes";

    public Reassigner(Map reassignConfig) {
        super(reassignConfig);
    }

    @Override
    public String getClassNameKey(Class clazz) {
        if (clazz == Identity.class) {
            return "Workgroup Owner";
        }

        return super.getClassNameKey(clazz);
    }

    /**
     * Change the owners of objects that are currently owned by the leaving identity
     * @param context persistence context
     * @param identityName the name of the identity that is leaving
     * @return the ReassignResult
     * @throws GeneralException unexpected exception, perhaps invalid configuration
     */
    public ReassignResult reassignOwnerships(SailPointContext context, String identityName)
            throws GeneralException
    {
        log.debug("Enter reassignOwnerships for identity " + identityName);

        // validate params
        if (Util.isEmpty(identityName)) {
            log.debug("Missing identityName");
            return EMPTY;
        }

        Identity identity = context.getObjectByName(Identity.class, identityName);
        if (identity == null) {
            log.debug("Unknown identity " + identityName);
            return EMPTY;
        }

        //Template Map
        Map<String,List<String>> ownershipMap = new HashMap<>();

        Map processConfig = getProcessConfig();
        if (Util.isEmpty(processConfig)) {
            // nothing to do
            log.debug("No leaver configuration found.  Skipping reassignments.");
            return EMPTY;
        }

        if (!isReassignmentEnabled()) {
            log.debug("Reassigning artifacts is disabled");
            return EMPTY;
        }

        List<String> reassignArtifactTypes = buildReassignArtifactTypes(processConfig);
        if (Util.isEmpty(reassignArtifactTypes)) {
            log.debug("reassignArtifactTypes is empty");
            return EMPTY;
        }

        Set<Class<? extends SailPointObject>> classesToReassign = new HashSet<>();
        for (Class<? extends SailPointObject> majorClass : ClassLists.MajorClasses) {
            if (reassignArtifactTypes.contains(majorClass.getSimpleName())) {
                classesToReassign.add(majorClass);
            }
        }
        if (Util.isEmpty(classesToReassign)) {
            log.debug("classesToReassign is empty");
            return EMPTY;
        }

        Identity newOwner = calculateNewOwner(context, identity, processConfig);

        try {
            for(Class<? extends SailPointObject> clazz : classesToReassign) {
                if (clazz != null) {
                    QueryOptions qo = new QueryOptions();
                    qo.addFilter(Filter.eq("owner", identity));
                    if (clazz == Identity.class) {
                        qo.addFilter(Filter.eq("workgroup", true));
                    }
                    List<String> ownedItemIds = new ArrayList<>();
                    Iterator<Object[]> objectIdsIterator = context.search(clazz, qo, "id");
                    if (objectIdsIterator != null) {
                        while (objectIdsIterator.hasNext()) {
                            Object[] objectIdArr = objectIdsIterator.next();
                            if (objectIdArr != null) {
                                String objectId = (String) objectIdArr[0];
                                if (objectId != null) {
                                    ownedItemIds.add(objectId);
                                }
                            }
                        }
                    }
                    Util.flushIterator(objectIdsIterator);
                    doReassignment(context, clazz, newOwner, ownedItemIds, ownershipMap, (spo, owner) -> spo.setOwner(owner));
                }
            }
        }
        finally {
            //Final Commit
            context.commitTransaction();
        }

        ReassignResult result= new ReassignResult(newOwner != null ? newOwner.getName() : null, ownershipMap);

        if (newOwner != null) {
            context.decache(newOwner);
        }

        if (log.isDebugEnabled()) {
            log.debug("Result map of reassignments: \n" + result.toDebugString());
        }

        log.debug("End reassignOwnerships for identity " + identityName);

        return result;
    }

    private List<String> buildReassignArtifactTypes(Map processConfig) {
        List<String> reassignTypes = new ArrayList<String>();
        if (!Util.isEmpty(processConfig)) {
            List<String> typeNames = (List<String>)processConfig.get(CFG_KEY_REASSIGN_TYPES);
            if (!Util.isEmpty(typeNames)) {
                reassignTypes = typeNames.stream().filter(item-> Util.isNotNullOrEmpty(item)).collect(Collectors.toList());
            }
        }
        return reassignTypes;
    }

}
