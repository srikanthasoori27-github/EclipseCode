/*
 *  (c) Copyright ${YEAR} SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.TreeNode;

import sailpoint.api.SailPointContext;
import sailpoint.classification.ClassificationResult;
import sailpoint.fam.CorrelationHelper;
import sailpoint.fam.FAMConnector;
import sailpoint.fam.PagedIterator;
import sailpoint.fam.Paging;
import sailpoint.fam.model.DataClassificationCategory;
import sailpoint.fam.model.FAMObject;
import sailpoint.fam.model.Permission;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

public class FAMClassificationService {

    private static Log _log = LogFactory.getLog(FAMClassificationService.class);

    SailPointContext _ctx;
    FAMConnector _connector;

    public static String FAM_MODULE_SOURCE = "FileAccessManager";

    public FAMClassificationService(SailPointContext ctx, FAMConnector connector) {
        this._ctx = ctx;
        this._connector = connector;

    }

    static int DFLT_PAGE_SIZE = 100;

    /**
     * Return an Iterator of ClassificationResults for the given QueryOptions
     * @param ops - QueryOptions, that will get translated to QueryParams for the SCIM API
     * @return Iterator of CLassificationResults
     * @throws GeneralException
     */
    public Iterator<ClassificationResult> getClassifications(QueryOptions ops) throws GeneralException {
        if (ops == null) {
            ops = new QueryOptions();
        }
        ops.add(Filter.notnull(Permission.PERM_FILTER_CLASSIFICATION_CAT));




        if (ops.getResultLimit() > 0) {
            //Only asking for a certain set. Return them all as an Iterator
            //Call Connector to get Response
            Response resp = null;
            try {
                resp = _connector.getObjects(Permission.class, ops, Arrays.asList(Permission.PERM_ATTR_CLASS_CAT));
                List<Permission> perms = new ArrayList<>();
                //Convert Response to Permission objects
                if (isSuccessStatus(resp)) {
                    _log.debug("Received success response from FAM");
                    perms = readListJsonFromEntity(Permission.class, resp);
                } else {
                    //Throw/null/???
                    _log.warn("Failed Response from FAM: " + resp);
                    throw new GeneralException("Error getting Classifications from FAM");
                }
                return new ClassificationTransformationIterator(perms.iterator());
            }
            finally {
                if (resp != null) {
                    resp.close();
                }
            }
        } else {
            //Asking for all Permissions, return paged iterator/
            //TODO: Default limit size? Configurable?
            Paging paging = new Paging(ops.getFirstRow() <= 0 ? 1 : ops.getFirstRow(), DFLT_PAGE_SIZE);

            QueryOptions finalOps = ops;
            PagedIterator<Permission> permIter = new PagedIterator<Permission>(paging, paging1 -> {

                //Call Connector to get Response
                finalOps.setFirstRow(paging1._start);
                finalOps.setResultLimit(paging1._limit);
                Response resp = null;
                try {
                    //Response r = connector.getPermissions(paging1, filters);
                    resp = _connector.getObjects(Permission.class, finalOps, Arrays.asList(Permission.PERM_ATTR_CLASS_CAT));
                    //Convert Response to Permission objects
                    List<Permission> perms;
                    if (isSuccessStatus(resp)) {
                        _log.debug("Received success response from FAM");
                        perms = readListJsonFromEntity(Permission.class, resp);
                    } else {
                        //Throw/null/???
                        _log.warn("Failed Response from FAM: " + resp);
                        throw new GeneralException("Error getting Classifications from FAM");
                    }
                    return perms.iterator();

                } catch (Exception e) {
                    throw new RuntimeException("Error getting objects" + e);
                }
                finally {
                    if (resp != null) {
                        resp.close();
                    }
                }
            });

            return new ClassificationTransformationIterator(permIter);
        }

    }

    /**
     * Return an Iterator of ClassificationResults for a given SailPointObject. This will translate the SailPointObject
     * to the ID used in FAM, and query for all classifications for that given resource
     * @param obj
     * @param ops
     * @return
     * @throws GeneralException
     */
    public Iterator<ClassificationResult> getClassifications(SailPointObject obj, QueryOptions ops)
        throws GeneralException {

        if (obj != null) {
            if (ops == null) {
                ops = new QueryOptions();
            }

            if (obj instanceof ManagedAttribute) {
                ops.add(Filter.eq(Permission.PERM_FILTER_GROUP_ID, CorrelationHelper.getCorrelationId(obj)));

            } else if (obj instanceof Link) {
                ops.add(Filter.eq(Permission.PERM_FILTER_USER_ID, CorrelationHelper.getCorrelationId(obj)));
            } else {
                _log.warn("Unknown SailPointObject type for classifications [" + obj.getClass().getSimpleName() + "]");
            }

            return getClassifications(ops);

        } else {
            _log.warn("No SailPointObject to fetch classifications");
            return null;
        }
    }

    private boolean isSuccessStatus(javax.ws.rs.core.Response response) {
        return ((response.getStatus()) >= 200 && (response.getStatus() < 300));
    }

    private <T> T readJsonFromEntity(Class<T> clazz, javax.ws.rs.core.Response response) throws GeneralException {
        MediaType responseMediaType = response.getMediaType();
        if((responseMediaType != null) && (!responseMediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE))) {
            throw new RuntimeException("This is not a json document");
        }

        String jsonString = response.readEntity(String.class);
        TreeNode node = null;
        try {
            TreeNode tn = JsonHelper.getObjectMapper().readTree(jsonString);
            //Objects are nested in the Resources entry. TODO: is this true for single resource?
            node = tn.get("Resources");
        } catch (IOException e) {
            throw new GeneralException("Exception parsing response" + e);
        }
        return JsonHelper.fromJson(clazz, node.toString());
    }

    private <T> List<T> readListJsonFromEntity(Class<T> clazz, javax.ws.rs.core.Response response) throws GeneralException {
        MediaType responseMediaType = response.getMediaType();
        if((responseMediaType != null) && (!responseMediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE))) {
            throw new RuntimeException("This is not a json document");
        }

        String jsonString = response.readEntity(String.class);
        TreeNode node = null;
        try {
            TreeNode tn = JsonHelper.getObjectMapper().readTree(jsonString);
            //Objects are nested in the Resources entry
            node = tn.get("Resources");
        } catch (IOException e) {
            throw new GeneralException("Exception parsing response" + e);
        }
        return JsonHelper.listFromJson(clazz, node.toString());
    }


    public ClassificationResult convertToClassificationResult(Permission p, Map<String,
            SailPointObject> correlationCache) throws GeneralException {

        ClassificationResult result = new ClassificationResult();

        result.setAttribute(ClassificationResult.ATT_RESPONSE, p);

        //Correlate
        FAMObject userOrGroup = p.getGroup() != null ? p.getGroup() : p.getUser();
        SailPointObject correlatedObject = CorrelationHelper.getSailPointObject(userOrGroup, _ctx, correlationCache);
        //Correlation Cache

        if (correlatedObject != null) {
            //Create ClassificationResult
            result.setObjectId(correlatedObject.getId());
            result.setObjectType(correlatedObject.getClass());

        } else {
            if (_log.isInfoEnabled()) {
                _log.info("Unable to correlate Permission to SailPointObject " + p);
            }
        }

            List<Classification> classifications = new ArrayList<>();
            if (p.getBusinessResource() != null) {
                for (DataClassificationCategory cat : Util.safeIterable(p.getBusinessResource().getClassificationCategories())) {
                    Classification classification = new Classification();
                    classification.setName(cat.getName());
                    //Allow consumer to determine/set locale? -rap
                    classification.setDescription(cat.getDescription());
                    classifications.add(classification);
                }
            }
        
            result.setClassifications(classifications);


        return result;

    }


    /**
     * Iterator returning ClassificationResults.
     * This takes an Iterator of Permission objets, and transforms them to ClassificationResults
     */
    public class ClassificationTransformationIterator implements Iterator<ClassificationResult> {

        Iterator<Permission> _permissionIterator;
        LRUMap<String, SailPointObject> _correlationCache;

        public ClassificationTransformationIterator(Iterator<Permission> permIter) {
            _permissionIterator = permIter;
            _correlationCache = new LRUMap<>(100);
        }

        @Override
        public boolean hasNext() {
            return _permissionIterator.hasNext();
        }

        @Override
        public ClassificationResult next() {
             ClassificationResult result = null;
            Permission p = _permissionIterator.next();
            if (p != null) {
                try {
                    //TODO: this can return null if not correlated. Do we want this? -rap
                    //Could return empty ClassificationResult?
                    result = convertToClassificationResult(p, _correlationCache);
                } catch (GeneralException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }
    }

    public void setConnector(FAMConnector connector) {
        this._connector = connector;
    }


    /**
     * Class to hold Correlation Cache
     */
    class LRUMap<K, V> extends LinkedHashMap {

        int _limit;

        public LRUMap(int limit) {
            //Access order true should keep latest access at back
            super(100, 0.75f, true);
            _limit = limit;

        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
             return size() > _limit;
        }




    }


}
