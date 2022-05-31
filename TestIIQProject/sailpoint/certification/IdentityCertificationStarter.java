/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Start the partitioned identity certification process.  The identities
 * to be included in the certification are determined and divided in to 
 * partitions.  Partition Request objects are launched.
 *
 * Author: Jeff
 *
 * This is normally called from a Quartz task with a pre-created TaskResult.
 * Once the partition requests have been saved the TaskResult is marked
 * as being partitioned so the TaskManager will leave it alone when the
 * launcher Quartz task finishes.
 *
 * For testing convenience it may also be called directly from unit tests
 * without using Quartz.  In that case we will generate a partitioned 
 * TaskResult.  This may also be useful in customizations that want to 
 * create a cert in response to an event but don't want to mess with tasks.
 *
 * ROOT CERTIFICATION
 *
 * Unlike the old framework, we're adding the concept of a root certification.
 * This is a Certification object created without entities that we use
 * for the duration of entity generation.  Entities are not added to 
 * Certifications until they are all generated to avoid contention on the
 * entity list among partitions.  But the old entity genereation code we're
 * using expects to find options in a Certification object.  I tried to 
 * convert some of this to use CertificationDefinition instead but there
 * are a few things in a Cert that are calculated from the definition and
 * I didn't want to recreate that calculation and cache it.  
 *
 * Of more concern are the rules which all expect to be passed a 
 * Certification object.  I don't know what they use that for, but
 * for better compatibility we'll continue that.  The rules can't however
 * rely on there being a non-empty entity list.
 *
 * When we finish buildling entities and know who all the certifiers are going
 * to be, we may or may not keep the root certification.
 *
 * QUESTIONS
 *
 * Does the CertificationDefinition support extended custom attributes?
 * If so rules could use them to configure behavior.
 *
 * Allow the target rule to return a QueryOptions instead of a Filter
 * to set the advanced options?
 *
 * NAMES
 * 
 * CertificationGroups are named in a typically convoluted way by
 * CertificationBuilderFactory which calls CertificationContext.getCertificationNamer
 * which for our purposes ends up in CertificationNamer. The name template
 * is from CertificationDefinition.getCertificationNameTemplate.
 *
 * Normally this is fine, but the old framework does not do any name qualification,
 * for unit tests this is bad since we can create certs rapidly.
 * For unit tests we will create our own TaskResult outside of Quartz
 * using TaskManager.saveQualifieName for name uniqueness.  This also
 * uses the certificationNameTemplate.  If we detect that we're outside of
 * Quartz we'll use the TaskResult name for the CertificationDefinition rather
 * than rendering the template to ensure uniqueness.
 *
 * Ideally we could handle name qualfication when using templates but it's
 * a hard thing to make happen when dealing with scheduled tasks we don't bother.
 *
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.api.certification.CertificationNamer;

import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Rule;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;

import sailpoint.task.TaskMonitor;

import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import sailpoint.request.CertificationBuilderExecutor;

public class IdentityCertificationStarter {


    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    // 
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(IdentityCertificationStarter.class);
    
    /**
     * The definition has an entry for certificationNameTemplate which
     * is used to name the CertificationGroup, and also appears to name
     * the CertificationDefinition itself.  If we get a sparse definition,
     * this will be the default template.
     */
    public final static String DEFAULT_NAME_TEMPLATE = "Targeted Certification [${fullDate}]";

    SailPointContext _context;
    TaskSchedule _schedule;
    TaskResult _result;
    Attributes<String,Object> _arguments;
    
    CertificationDefinition _definition;
    CertificationGroup _group;
    Certification _certification;

    /**
     * Handler when we run synchronsly without creating partitions.
     */
    PartitionHandler _handler;

    boolean _terminate;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Interface called when being run from a task.
     */
    public IdentityCertificationStarter(SailPointContext context,
                                        TaskSchedule sched,
                                        TaskResult result,
                                        Attributes<String,Object> args)
        throws GeneralException {

        _context = context;
        _schedule = sched;
        _result = result;
        // these are the flattened arguments from the TaskSchedule
        // and the TaskDefinition
        _arguments = args;
    }
    
    /**
     * Constructor for unit tests.
     * Consider making NewCertificationExecutor permanent and use
     * that instead for tests.
     */
    public IdentityCertificationStarter(SailPointContext context,
                                        Attributes<String,Object> args)
        throws GeneralException {

        _context = context;
        _arguments = args;
    }
    
    /**
     * Constructor for unit tests.
     */
    public IdentityCertificationStarter(SailPointContext context,
                                        CertificationDefinition def)
        throws GeneralException {

        _context = context;
        _arguments = new Attributes<String,Object>();
        _definition = def;
        _result = createTaskResult();
    }

    public TaskResult getTaskResult() {
        return _result;
    }

    public Certification getCertification() {
        return _certification;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Start the generation process.
     * First locate or create a CertificationDefinition, then 
     * determine the target identities, and finally launch the 
     * partition requests.
     * 
     * TODO: If we have lenghty processes before launching partitions,
     * may want to pass the TaskMonitor down and support async termination.
     */
    public void execute() throws Exception {

        final String MeterName = "IdentityCertificationStarter: execute";
        Meter.reset();
        Meter.enter(MeterName);

        // if we're outside a task, fake up a result
        if (_result == null) {
            _result = createTaskResult();
        }

        // We're bypassing TaskMonitor management of partition results
        // and always forcing distributed results.  Make sure this flag
        // is off so we don't confuse RequestProcessor later.  Really
        // need to rip all this out and always use distributed results.
        _result.setConsolidatedPartitionResults(false);

        // Only flexible certs support restart
        _result.setRestartable(true);
        
        try {
            // Load or bootstrap a CertificationDefinition
            resolveCertificationDefinition();

            // Create the CertificationGroup, this is always done even
            // if there will be no Certifications
            createCertificationGroup();

            // determine the identities to include
            List<String> names = getTargetIdentities();

            if (Util.isEmpty(names)) {
                // old framework will not create empty Certifications,
                // stop now but leave the completed group (campaign) behind
                if (_group != null) {
                    _group.setStatus(CertificationGroup.Status.Complete);
                    _context.saveObject(_group);
                    _context.commitTransaction();
                }
            }
            else if (!_terminate) {
                // Create the temporary root certification
                createRootCertification();
                
                List<Request> partitions = buildPartitions(names);

                if (!_terminate) {
                    if (_definition.isEnablePartitioning()) {
                        launchPartitions(partitions);
                    }
                    else if (partitions.size() == 2) {
                        // run synchronous if partitioning disabled
                        // buildPartitions will still have built them but we
                        // use a special PartitionHandler method
                        TaskMonitor monitor = new TaskMonitor(_context, _result);
                        _handler = new PartitionHandler(_context);
                        _handler.executeSynchronous(monitor, partitions.get(0), _definition, _group, _certification);
                        // In theory we shouldnt have to do this, but CertificationExecutor will overwrite our
                        // changes since it expects the same result object it sent. This only happens for synchronous execution
                        // which we dont expect to be used much, so just merge it to the original result.
                        _result.merge(monitor.getTaskResult());
                    }
                    else {
                        // buildPartitions didn't handle the enablePartitioning flag correctly
                        log.error("Unexpected number of partitions");
                    }
                }
            }
            
            Meter.exit(MeterName);
            if (_arguments.getBoolean("profile")) {
                // store the formatted report, it's easier
                // ugh, no it isn't, we convert newlines to character entitities,
                // unless we can teach the serializer to use CDATA sections it
                // is unreadable
                // String report = Meter.generateReport();
                List<Map<String,String>> report = Meter.render();
                _result.put("profile", report);
            }

            // if outside Quartz, save final result
            if (_schedule == null) {
                _context.decache();
                _context.saveObject(_result);
                _context.commitTransaction();
            }
        }
        catch (Throwable t) {
            if (_schedule != null) {
                // we're in a Quartz task, let TaskManager deal with it
                throw t;
            }
            else {
                try {
                    System.out.println(Util.stackToString(t));
                    _context.decache();
                    _result.addException(t);
                    _result.setTerminated(true);
                    _context.saveObject(_result);
                    _context.commitTransaction();
                }
                catch (Throwable t2) {
                    log.error("Unable to save final TaskResult");
                }
            }
        }

    }

    /**
     * Handle a TaskExecutor termination request when run as a synchronous task
     * rather than partitioned.  CertificationExecutor will call this.
     * Since the construction of the entity name list can take some time
     * need to support termiantion during that as well as propagating
     * to the handler if we decide to use one.
     */
    public void terminate() {
        _terminate = true;
        if (_handler != null) 
            _handler.terminate();
    }

    /**
     * Launch the partition requests.
     * This is functionally equivalent to AbstractTaskExecutor.launchPartitions,
     * so much so that we should factor it out into a shared utility.
     */
    private void launchPartitions(List<Request> partitions)
        throws GeneralException {

        // let the TaskManager know that this result is to be left alive
        // and not touch it any further
        _result.setPartitioned(true);
        
        // Root result must have an embedded result for each partition
        // in order to detect when all partitions are complete and
        // complete the root result.
        // Don't like how this behaves for long partition lists
        for (Request req : partitions) {
            _result.getPartitionResult(req.getName());
        }
        _context.saveObject(_result);
        _context.commitTransaction();
        
        int requestsCreated = 0;
        try {
            for (Request req : partitions) {

                // Request must point back to the TaskResult
                req.setTaskResult(_result);

                // though not required it's a good idea to carry
                // over the task launcher
                req.setLauncher(_result.getLauncher());

                // do we really need this?  I wish this wasn't an enum
                req.setType(TaskItemDefinition.Type.Partition);

                // RequestManager.addRequest(_context, req);
                _context.saveObject(req);
                requestsCreated++;
            }
            _context.commitTransaction();
        }
        catch (Throwable t) {
            log.error("Exception trying to launch partitioned requests!");
            log.error(t);
            if (requestsCreated == 0) {
                // If we were not able to create any requests, then terminate without
                // creating a shared result.  
                _result.setPartitioned(false);
                _result.addMessage(Message.error(t.getLocalizedMessage()));
            }
            else {
                // At least one request is out there and may be running, have
                // to ask for an orderly shutdown.
                // ugh, adding the error requires locking, since we're  likely
                // in Hibernate panic mode don't bother, the shutdown probably
                // won't work either
                TaskManager tm = new TaskManager(_context);
                tm.terminate(_result);
            }
        }
    }

    /**
     * Create a TaskResult to own the partitions when executed outside
     * of a Quartz task.
     * 
     * TODO: Support the result name being passed in.
     *
     * For manager certs, the default task name is
     * "Manager Cerfication [DATE] [1/12/18 4:04 PM]
     * This looks similar to the name of the CertificationDefinition
     * but that doesn't include "[DATE]".  Might want to use the 
     * certificationNameTemplate from the definition here?
     * 
     * Would be nice to wait until the CertificationGroup has been created
     * and use that name but it complicates error handling in execute().
     */
    private TaskResult createTaskResult()
        throws GeneralException {

        final String TaskDefName = "Certification Manager";

        TaskResult res = new TaskResult();

        TaskDefinition taskDef = _context.getObjectByName(TaskDefinition.class, TaskDefName);
        if (taskDef == null)
            throw new GeneralException("No task definition: " + TaskDefName);

        res.setDefinition(taskDef);
        res.setLauncher(_context.getUserName());
        res.setHost(Util.getHostName());
        res.setLaunched(new Date());
        res.setType(TaskDefinition.Type.Certification);
        res.setPartitioned(true);
        res.setName(getCertificationName());
        
        // we don't really need this now that we're including the timestamp
        // MT: actually name template is not guaranteed to have a timestamp so we do need this.
        TaskManager tm = new TaskManager(_context);
        tm.saveQualifiedResult(res, true, true);

        return res;
    }

    /**
     * Render the TaskResult or CertificationGroup name.
     * See comments at the top for more on naming subtleties
     * in the unit tests vs. normal scheduling.
     * 
     * CertificationNamer is relatively simple so we'll reuse it
     * It uses MessageRenderer internally with different sets of
     * parameters like "fullDate"
     */
    private String getCertificationName()
        throws GeneralException {

        String template = null;
        
        // definition may be missing if we're called from a unit test
        if (_definition != null)
            template = _definition.getCertificationNameTemplate();

        if (template == null)
            template = DEFAULT_NAME_TEMPLATE;
        
        CertificationNamer namer = new CertificationNamer(_context);
        return namer.render(template);
    }
    
    /**
     * Locate or create the CertificationDefinition.
     * 
     * When called from a task scheduled normally, the id of the definition
     * will be inthe TaskSchedule arguments.  CertificationExecutor also
     * supported passing the id in the TaskDefinition arguments, "only for the
     * scale data and demo data setup where we want to launch a task
     * immediately to create the certification without having to use
     * a schedule".  The args map passed to the constructor will have
     * the merged args so we don't have to make that distinction here.
     *
     * When a definition id is passed, the definition is assumed to be
     * fully initialized.  
     *
     * For testing convenience, we also allow a partially initialized
     * definition to be passed in, or we can build one from the task arguments.
     */
    private void resolveCertificationDefinition()
        throws GeneralException {

        if (_definition != null) {
            // passed in from the unit tests, have to finish initialization
            // then override with what was passed in
            _definition = createDefinition(_definition.getAttributes());
        }
        else {
            //TODO: This was named ID, but we now set it as name?! -rap
            String defId = _arguments.getString(CertificationSchedule.ARG_CERTIFICATION_DEFINITION_ID);
            if (defId != null) {
                _definition = _context.getObjectByName(CertificationDefinition.class, defId);
                if (_definition == null)
                    throw new GeneralException("Invalid CertificationDefinition name: " + defId);
                // assume it was fully initialized
            }
            else {
                // create one from task arguments
                _definition = createDefinition(_arguments);
            }
        }

        // if we had to create one, persist it
        if (_definition.getId() == null) {
            _context.saveObject(_definition);
            _context.commitTransaction();
        }
    }

    /**
     * Generate a default CertificationDefinition, allowing attributes
     * to be overridden with an arguments map.  This is used in unit
     * tests and for testing with the console where you can define
     * everything as task arguments without precreating a CertificationDefinition
     * and putting it in a TaskSchedule.
     */
    private CertificationDefinition createDefinition(Attributes<String,Object> overrides)
        
        throws GeneralException {

        CertificationDefinition def = new CertificationDefinition();

        // populates it with system config defaults
        def.initialize(_context);

        if (overrides != null) {
            // in theory this could have things in it that don't belong
            // but they shouldn't hurt
            def.mergeAttributes(overrides);
        }
        
        // don't let this be overridden
        def.setType(Certification.Type.Focused);

        // the owner defaults to "spadmin" but can be changed in the UI
        // note that owner is not the same as the certifiers list
        if (def.getCertificationOwner() == null)
            def.setCertificationOwner("spadmin");

        // See comments at the top over naming nuances between unit tests
        // and normally scheduled certs.  If we're outside of Quartz use
        // the qualified TaskResult name
        if (_schedule == null)
            def.setName(_result.getName());
        else
            def.setName(getCertificationName());
    
        // flexi certs now require a certifier selection type
        if (def.getCertifierSelectionType() == null) {
            def.setCertifierSelectionType(CertifierSelectionType.Manual);
            if (def.getCertifierName() == null)
                def.setCertifierName("spadmin");
        }

        if (def.getBackupCertifierName() == null) {
            def.setBackupCertifierName("spadmin");
        }
        
        // pain to have to remember this since we always use a filter
        // could support others though and auto-select
        if (def.getEntitySelectionType() == null) {
            def.setEntitySelectionType(CertificationDefinition.ENTITY_SELECTION_TYPE_FILTER);
        }
        
        return def;
    }

    /**
     * Create the CertificationGroup, what is called the "campaign" in the UI.
     * 
     * This simulates CertificationBuilderFactory.initializeCertificationGroups
     * and CertificationHelper.createCertificationGroup.
     *
     * Old code has comments "certs scheduled before 5.1 will not have a
     * certification group owner defined, so don't attempt to create a group
     * for those certs".  Assuming we don't have to deal with that.
     */
    private void createCertificationGroup()
        throws GeneralException {

        CertificationGroup group = new CertificationGroup();
        
        group.setType(CertificationGroup.Type.Certification);
        group.setStatus(CertificationGroup.Status.Pending);
        group.setDefinition(_definition);
        group.setAssignedScope(_definition.getAssignedScope());
        
        // See comments at the top over naming nuances between unit tests
        // and normally scheduled certs.  If we're outside of Quartz use
        // the qualified TaskResult name
        if (_schedule == null)
            group.setName(_result.getName());
        else {
            // Ensure unique name even if template does not include date.
            String name = ObjectUtil.generateUniqueName(_context, null, getCertificationName(), CertificationGroup.class, 0);
            group.setName(name);
        }

        
        String ownerName = _definition.getCertificationOwner();
        if (ownerName == null) {
            // Should be set by now, warn?
            log.warn("No certification owner specified in definition.  Defaulting to spadmin.");
            ownerName = "spadmin";
        }
        
        // should we enforce that this be non-null?
        Identity owner = _context.getObjectByName(Identity.class, ownerName);
        if (owner == null)
            throw new GeneralException("Invalid identity name for certification owner: " + ownerName);
        
        group.setOwner(owner);

        if (_schedule != null) {
            //Get the frequency of the first cron expression only
            String cronString = _schedule.getCronExpression(0);
            if (cronString != null) {
                CronString cs = new CronString(cronString);
                group.setAttribute(CertificationGroup.SCHEDULE_FREQUENCY, cs.getFrequency());
            }
        }

        _context.saveObject(group);
        _context.commitTransaction();
        _group = group;

        // Save the group name in the task result so we can link
        // them up in the unit tests.  Using the result name is not reliable.
        _result.put("certificationGroup", group.getName());
    }

    /**
     * Create the root Certification.
     * See file comments for more on what this is.
     */
    private void createRootCertification() throws GeneralException {

        CertificationInitializer ci = new CertificationInitializer(_context, _definition, _group);

        _certification = ci.createCertification(null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Target Identity Resolution
    //
    // Break this out into it's own class when it gets big enough.
    // This can take time so consider pushing this into an initial partition.
    //
    //////////////////////////////////////////////////////////////////////    

    /**
     * Calculate a list of Identity names from the identity filters
     * defined in the CertificationDefinition.  Normally I'd use ids
     * here, but some of the older options use names and that will be more
     * convenient for rule writers.  Just make sure the consumers support both.
     *
     * TODO: We've had lists of millions of ids in memory for testing,
     * but it might be better to spool them to suspended partition Requests
     * as we cursor over the ids?  
     *
     * The original "Identity" cert has an identity list inside the 
     * CertificadtionDefinition accessed with getIdentitiesToCertify,
     * may as well support that too.
     *
     * Simple exclusion like excludeInactive could be done up here
     * by adding something to the filter from the definition.
     * excludeFat requires more analysis and is better pushed into the
     * partition, though it would be nice to have a rought idea so we
     * can better balance the partitions.
     *
     * Originally supported all types and merged, but the new UI has
     * an "entityType" to select a single method.  
     */
    private List<String> getTargetIdentities()
        throws GeneralException {

        List<String> names = new ArrayList<String>();

        String selectionType = _definition.getEntitySelectionType();
        if (selectionType == null) {
            doNameList(names);
            doEntityFilter(names);
            doEntityRule(names);
            doEntityPopulation(names);
        }
        else if (CertificationDefinition.ENTITY_SELECTION_TYPE_FILTER.equals(selectionType)) {
            doEntityFilter(names);
        }
        else if (CertificationDefinition.ENTITY_SELECTION_TYPE_RULE.equals(selectionType)) {
            doEntityRule(names);
        }
        else if (CertificationDefinition.ENTITY_SELECTION_TYPE_POPULATION.equals(selectionType)) {
            doEntityPopulation(names);
        }
        
        // TODO: excludeInactive, excludeFat
        // option to have the name list represent managers with auto-target generation
        // wasn't discussed in grooming but would be easy

        // Remove any duplicates from our name list to keep it clean. We can get duplicates if we have more than one
        // method of getting names, or if any of our filters use a join.
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    /**
     * Look for a static list of names.
     * This is the convention the old generator used, we don't have this
     * in the UI but it makes sense to support it, at least for testing.
     */
    private void doNameList(List<String> names) {
        
        List<String> defNames = _definition.getIdentitiesToCertify();
        if (defNames != null) {
            names.addAll(defNames);
        }
    }
    
    /**
     * Calculate the entities using a Filter.
     */
    private void doEntityFilter(List<String> names)
        throws GeneralException {

        runTargetQuery(_definition.getEntityFilter(), names);
    }

    /**
     * Calculate a list of entities using a population.
     */
    private void doEntityPopulation(List<String> names)
        throws GeneralException {
        
        String name = _definition.getEntityPopulation();
        if (name != null) {
            GroupDefinition def = _context.getObjectByName(GroupDefinition.class, name);
            if (def == null) {
                log.warn("Invalid population name: " + name);
            }
            else {
                Filter filter = def.getFilter();
                // warn if null?
                runTargetQuery(filter, names);
            }
        }
    }

    /**
     * Run an identity query using an optional filter and add the names to the list.
     */
    private void runTargetQuery(Filter filter, List<String> names)
        throws GeneralException {

            List<String> props = new ArrayList<String>();
            props.add("name");
            QueryOptions ops = null;
            if (filter != null) {
                ops = new QueryOptions();
                ops.add(filter);
            }
            // TODO: What about scoping? Should that be an option?
            Iterator<Object[]> it = _context.search(Identity.class, ops, props);
            while (it.hasNext()) {
                Object[] row = it.next();
                String name = (String)(row[0]);
                names.add(name);
            }
    }

    /**
     * Calculate the entities using a rule.
     * The rule name return a Filter or a List<String> of names.
     *
     * TODO: Allow the rule to return an "all" indicator.
     */
    private void doEntityRule(List<String> names)
        throws GeneralException {

        String ruleName = _definition.getEntityRule();
        if (ruleName != null) {
            Rule rule = _context.getObjectByName(Rule.class, ruleName);
            if (rule == null) {
                // throw or move on?
                // should put something in the TaskResult
                log.error("Invalid rule name: " + ruleName);
            }
            else {
                Map<String,Object> inputs = new HashMap<String,Object>();
                // what to pass?
                // CertificationDefinition might make sense if it has
                // customizeable attributes, does it?
                // Could pass TaskResult to let the add errors.
                inputs.put("definition", _definition);
                
                Object result = _context.runRule(rule, inputs);

                if (result instanceof Filter) {
                    // TODO: may be nice to allow the rule to return
                    // a QueryOptions so they can turn scoping on or off
                    // or add "distinct" for join queries
                    runTargetQuery((Filter)result, names);
                }
                else if (result instanceof List) {
                    for (Object o : (List)result) {
                        if (o instanceof String) {
                            names.add((String)o);
                        }
                        else {
                            // don't allow Identity objects to discouarage the
                            // rules from brining them in
                            log.error("Invalid object in rule response list: " + o);
                        }
                    }
                }
                else if (result instanceof String) {
                    // I suppose we could take a single name
                    // but requiring a list isn't bad
                    names.add((String)result);
                }
                else {
                    // don't allow Identity objects to discouarage the
                    // rules from brining them in
                    log.error("Invalid response from rule: " + result);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Partition Building
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given the list of target identity names, break them up into partitions.
     * Just using a simple count at the moment, but it would be nice
     * to use "fatness" staistics to balance the partition.  That analysis is
     * not cheap however and adds pre-processing overhead.
     *
     * TOOD: Use name compression, I think Dan added that for something.
     */
    private List<Request> buildPartitions(List<String> names)
        throws GeneralException {

        List<Request> partitions = new ArrayList<Request>();

        String defName = CertificationBuilderExecutor.DEFINITION_NAME;
        RequestDefinition reqdef = _context.getObjectByName(RequestDefinition.class, defName);
        if (reqdef == null) 
            throw new GeneralException("Missing RequestDefinition: " + defName);

        if (names != null && names.size() > 0) {
            
            // if partitionSize is left zero we'll dump them all into one
            int partitionSize = 0;
            
            if (_definition.isEnablePartitioning()) {
                // this is what we supported for awhile
                // still allow it for testing
                partitionSize = _definition.getPartitionSize();
                if (partitionSize == 0) {
                    // preferred way with a partition count
                    int partitionCount = Util.otoi(_definition.getPartitionCount());
                    if (partitionCount == 0) {
                        // calculate the minimum to saturate available server threads
                        TaskManager tm = new TaskManager(_context);
                        partitionCount = tm.getSuggestedPartitionCount(defName);
                        if (partitionCount == 0) {
                            // request servers must all be dead, go ahead and
                            // schedule some for when they come up later
                            partitionCount = 4;
                        }
                    }
                    // this can have a roundoff error, we'll handle that in
                    // the loop below so we don't end up with a final partition
                    // with one thing in it
                    partitionSize = names.size() / partitionCount;
                    if (partitionSize == 0) {
                        // fewer names than partitions
                        // I guess one each, but if they entered an aburdly large
                        // partition count we could be smarter about readjusting that
                        partitionSize = 1;
                    }
                }
            }

            Request partition = null;
            List<String> partitionIdentities = null;
            // need any flexibility on this?
            String partitionName = "Partition ";
            int partitionNumber = 1;
            int total = 0;
            int entities = 0;
            
            for (String name : names) {
                if (partition == null) {
                    partition = initPartition(reqdef);
                    partitions.add(partition);
                    partition.setName(partitionName + Util.itoa(partitionNumber));
                    partitionIdentities = new ArrayList<String>();
                    partition.put(PartitionHandler.ARG_IDENTITIES, partitionIdentities);
                    partition.setPhase(1);
                    partitionNumber++;
                    entities = 0;
                }

                partitionIdentities.add(name);
                entities++;
                total++;
                if (partitionSize > 0 && entities >= partitionSize) {
                    // If there was an odd number divisor and we're only
                    // off by one, include it in the last partition.
                    // Could also have a threshold and include theh remainder
                    // if it is small
                    if (total + 1 != names.size()) {
                        partition = null;
                    }
                }
            }

            log.info("Generated " + Util.itoa(partitions.size()) + " entity partitions");

            // example cleanup phase, will need phases for
            // at least ownership assignment and stitching the Certifications togetyer
            Request cleanup = initPartition(reqdef);
            partitions.add(cleanup);
            cleanup.setName("Cleanup");
            cleanup.setPhase(2);
        }

        return partitions;
    }

    /**
     * Create a stub partition with the standard arguments.
     */
    private Request initPartition(RequestDefinition def) {

        Request req = new Request();
        req.setDefinition(def);
        req.setTaskResult(_result);

        req.put(CertificationBuilderExecutor.ARG_DEFINITION, _definition.getName());
        req.put(PartitionHandler.ARG_GROUP, _group.getName());
        // wanted to use the name here for easier debugging but
        // by default the cert naming template does not include a timestamp
        // so it is easy to have duplicate names
        req.put(PartitionHandler.ARG_CERTIFICATION, _certification.getId());

        return req;
    }

}
