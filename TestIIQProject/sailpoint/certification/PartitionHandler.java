/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Handle one identity certification partition.
 * Called by CertificationBuilderExecutor
 *
 * Author: Jeff
 *
 * I decided have the executor forward over to this package to keep the 
 * code in one place since this is likely to become more complicated.  In the
 * future the executor can be used for any cert type, and just forward
 * to the appropriate handler.
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdIterator;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestState;
import sailpoint.object.TaskResult;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class PartitionHandler {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(PartitionHandler.class);

    /**
     * Request argument with the CertificationGroup name.
     */
    public static final String ARG_GROUP = "group";

    /**
     * Request argument with the root Certification id.
     * Unlike group this is an id because the default naming template
     * does not include a timestamp. 
     */
    public static final String ARG_CERTIFICATION = "certification";
    
    /**
     * Request argument with the list of entity names to build.
     */
    public static final String ARG_IDENTITIES = "identities";

    /**
     * Name of an attribute in the RequestState containing the started flag.
     */
    public static final String STATE_STARTED = "started";

    /**
     * Result we save if we skipped entities due to failover.
     */
    public static final String RES_SKIPPED_ENTITIES = "skippedEntities";

    SailPointContext _context;
    TaskMonitor _monitor;
    Request _request;
    Attributes<String,Object> _arguments;
    CertificationDefinition _definition;
    EntityBuilder _builder;
    boolean _terminate;
    
    /**
     * Number of entities we skipped due to failover.
     */
    int _skipped;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Initialization
    //
    //////////////////////////////////////////////////////////////////////

    public PartitionHandler(SailPointContext context,
                            TaskMonitor monitor,
                            Request request,
                            Attributes<String,Object> args,
                            CertificationDefinition def) {
        _context = context;
        _monitor = monitor;
        _request = request;
        _arguments = args;
        _definition = def;
    }

    /**
     * Constructor only for executeSynchronous()
     *
     */
    public PartitionHandler(SailPointContext context) {
        _context = context;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Locate or Bootstrap a Certification to own the entities
     * in this partition.  This is temporary.
     *
     * Call EntityBuilder to create entitiets and add them
     * to the certification.
     */
    public void execute() throws GeneralException {

        // immediately remember the start time so we subtract
        // request processor overhead from the elapsed TaskDefinition time
        TaskResult partitionResult = _request.bootstrapPartitionResult();
        partitionResult.put("startTime", Util.ltoa(System.currentTimeMillis()));

        int phase = _request.getPhase();
        boolean showRecommendations = getShowRecommendations(_definition);
            
        if (log.isInfoEnabled()) {
            log.info("Start identity partition handler, phase: " + Util.itoa(phase));
        }

        CertificationGroup group = getCertificationGroup();

        // currently only set with task arguments, not a formal definition attribute
        boolean profile = Util.otob(_definition.getAttribute("profile"));
        if (profile)
            Meter.reset();
            
        if (phase == 1) {
            List<String> names = getIdentities(group);
            // if we skipped any, remember this
            if (_skipped > 0) {
                partitionResult.put(RES_SKIPPED_ENTITIES, Util.itoa(_skipped));
            }
            if (!_terminate) {
                Certification cert = getRootCertification();
                _builder = new EntityBuilder(_context, _monitor, _definition, group, cert, showRecommendations);
                _builder.buildEntities(names);
            }
        }
        else if (phase == 2) {
            // start the certs and finalize
            // pass in the root result, not ours
            // this one cannot currently be restarted, it wouldn't be hard
            // but this shouldn't take too long
            TaskResult rootResult = _request.getTaskResult();
            CertificationFinalizer cf = new CertificationFinalizer(_context, rootResult, _definition, group);
            cf.execute();
        }
        else {
            log.error("Unknown phase: " + phase);
        }
        
        if (profile) {
            // old code would use TaskMonitor to lock the partition result but
            // here we can assume we're using distributed results
            List<Map<String,String>> report = Meter.render();
            partitionResult.put("profile", report);
        }

        // should be redundant with metering
        partitionResult.put("endTime", Util.ltoa(System.currentTimeMillis()));
        
        if (log.isInfoEnabled()) {
            log.info("End identity partition handler, phase: " + Util.itoa(phase));
        }
    }

    /**
     * Pass a termination request through to the builder so it can break
     * out of the loop.
     */
    public void terminate() {
        _terminate = true;
        if (_builder != null)
            _builder.terminate();
    }

    private CertificationGroup getCertificationGroup() throws GeneralException {

        String name = _arguments.getString(ARG_GROUP);
        if (name == null)
            throw new GeneralException("Missing CertificationGroup name");

        CertificationGroup group = _context.getObjectByName(CertificationGroup.class, name);
        if (group == null)
            throw new GeneralException("Invalid CertificationGroup: " + name);

        return group;
    }
    
    private Certification getRootCertification() throws GeneralException {

        String id = _arguments.getString(ARG_CERTIFICATION);
        if (id == null)
            throw new GeneralException("Missing Certification id");

        Certification cert = _context.getObjectById(Certification.class, id);
        if (cert == null)
            throw new GeneralException("Invalid Certification: " + id);

        return cert;
    }

    /**
     * Derive the list of identity names to certify.
     * For initial execution this comes from the arguments map.
     * For failover execution, we remove already processed names.
     * 
     * This is more complicated than agg/refresh because we can't
     * allow duplicate entities to be created.  So we always have to 
     * check for existing entities if we know we're restarting.
     * Using a rather large hammer and querying for all entities in the group
     * which is nice because it avoids the need to store a processed id
     * list in the RequestState, but may bring in quite a bit of stuff 
     * we don't need.
     * 
     * If we did save periodic state, this would have to probe the remainder name
     * list until it found one that didn't have an entity, then start from there.
     */
    private List<String> getIdentities(CertificationGroup group)
        throws GeneralException {

        // Save something in the RequestState immediately so we know that
        // we've started if this partition dies
        boolean restarting = false;
        RequestState state = _monitor.getPartitionedRequestState();
        if (Util.otob(state.get(STATE_STARTED))) {
            restarting = true;
        }
        else {
            // this does it differently than the others since we don't
            // have complex state and don't need to pass it through the RequestExecutor
            state.put(STATE_STARTED, true);
            _context.saveObject(state);
            _context.commitTransaction();
        }

        List<String> names = _arguments.getStringList(ARG_IDENTITIES);
        if (restarting && Util.size(names) > 0) {
            names = filterNames(state, group, names);
        }

        return names;
    }

    /**
     * Filter the name list from the partition to remove the names
     * of entities that have already been processed.  
     *
     * This one uses the "entity probe" method.  It has better
     * memory characteristics but will result in many small 
     * queries.
     */
    private List<String> filterNames(RequestState state, CertificationGroup group,
                                     List<String> names)
        throws GeneralException {

        // we have to make multiple probes for each cert in the group
        // tagging entities with partition name would be simipler but
        // adds yet another index
        List<String> certIds = new ArrayList<String>();
        QueryOptions certOps = new QueryOptions();
        certOps.add(Filter.contains("certificationGroups", group));
        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> it = _context.search(Certification.class, certOps, props);
        while (it.hasNext()) {
            Object[] row = it.next();
            String id = (String)row[0];
            certIds.add(id);
        }
        
        List<String> filtered = new ArrayList<String>();
        // can stop probing after we find the first missing
        boolean found = false;
        
        for (String name : Util.iterate(names)) {
            if (found) {
                filtered.add(name);
            }
            else {
                // have to look in every cert int the group
                // could use an IN here if the list isn't long
                int count = 0;
                for (String cert : Util.iterate(certIds)) {
                    QueryOptions entOps = new QueryOptions();
                    entOps.add(Filter.eq("certification.id", cert));
                    entOps.add(Filter.eq("identity", name));
                    count += _context.countObjects(CertificationEntity.class, entOps);
                }

                if (count == 0) {
                    filtered.add(name);
                    // crap, since order is defined by the name list in the partition and
                    // not sql it is stable and in theory we could stop on the first unprocessed hit
                    // but this doesn't factor in empty entities that were processed but didnt
                    // have an entity created, things like "spadmin" and our test users
                    // and customers commonly have empty users, have to probe every one
                    // could avoid this by pruning the partition names at the same time
                    // we prune entities but that's a lot of work for questionable gain
                    // found = true;
                    if (log.isInfoEnabled()) {
                        log.info("Adding unprocessed identity: " + name);
                    }
                }
                else {
                    if (log.isInfoEnabled()) {
                        log.info("Filtering previously processed identity: " + name);
                    }
                    _skipped++;
                }
            }

            if (_terminate)
                break;
        }
        
        return filtered;
    }

    /**
     * Filter the name list from the partition to remove the names
     * of entities that have already been processed.  
     *
     * This one uses the "load all names at once" method.  It
     * reduces the number of individual queries but has larger
     * memory overhead.
     */
    private List<String> filterNamesAlt(RequestState state, CertificationGroup group,
                                        List<String> names)
        throws GeneralException {

        Map<String,String> processed = new HashMap<String,String>();

        QueryOptions certops = new QueryOptions();
        certops.add(Filter.contains("certificationGroups", group));

        IdIterator it = new IdIterator(_context, Certification.class, certops);
        while (it.hasNext()) {
            String id = it.next();
            QueryOptions entityops = new QueryOptions();
            entityops.add(Filter.eq("certification.id", id));
            List<String> props = new ArrayList();
            props.add("identity");

            Iterator<Object[]> it2 = _context.search(CertificationEntity.class, entityops, props);
            while (it2.hasNext()) {
                Object[] row = it2.next();
                String entname = (String)row[0];
                processed.put(entname, entname);
            }
        }
        
        List<String> filtered = new ArrayList<String>();
        for (String name : Util.iterate(names)) {
            if (processed.get(name) == null) {
                filtered.add(name);
            }
        }

        return filtered;
    }

    /**
     * Get the value of the showRecommendations setting. If it's set to true in the definition, but a
     * recommender cannot be instantiated, then force to false for this execution.
     *
     * @param def The certification definition for this cert
     */
    private boolean getShowRecommendations(CertificationDefinition def) {
        boolean show = Util.nullsafeBoolean(def.getShowRecommendations());
        if (show && !RecommenderUtil.isRecommenderConfigured(_context)) {
            show = false;
            log.warn("Recommendations have been disabled for this certification because a Recommender could not be created.");
        }

        return show;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Synchronous Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Special case to execute all of the phases synchronously outside
     * the request processor.  Used only by IdentityCertificationStarter
     * when there is only one partition.  
     *
     * In here so we can more easily see when the code above goes out of sync.
     * Could avoid the duplication, but then we would have to fake a
     * RequestHandler environment which is about as complicated.
     *
     * If we ever add more phases reconsider this.
     */
    public void executeSynchronous(TaskMonitor monitor,
                                   Request partition,
                                   CertificationDefinition def,
                                   CertificationGroup group,
                                   Certification cert)
        throws GeneralException {

        if (log.isInfoEnabled()) {
            log.info("Start synchronous execution");
        }

        // Don't reset meters here, let IdentityCertificationStarter manage that

        List<String> names = Util.getStringList(partition.getAttributes(), ARG_IDENTITIES);
        if (names == null || names.size() == 0) {
            throw new GeneralException("Empty name list");
        }
        else {
            boolean showRecommendations = getShowRecommendations(def);
            _builder = new EntityBuilder(_context, monitor, def, group, cert, showRecommendations);
            _builder.buildEntities(names);
        }

        // it is important to decache before finalizing because the Certification in the
        // session doesn't know that entities have been added
        _context.decache();

        if (!_terminate) {
            // start the certs and finalize
            // pass in the root result, not ours
            CertificationFinalizer cf = new CertificationFinalizer(_context, monitor.getTaskResult(), def, group);
            cf.execute();
            // Refetch the task result to get the changes during finalization
            monitor.reattach(_context.getObjectById(TaskResult.class, monitor.getTaskResult().getId()));
        
        }
        
        if (log.isInfoEnabled()) {
            log.info("Finish synchronous execution");
        }
    }
    
}
