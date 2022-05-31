/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Describer;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Describable;
import sailpoint.object.Filter;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.Policy;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class LocalizedAttributeSyncExecutor extends AbstractTaskExecutor {

	private static Log log = LogFactory.getLog(LocalizedAttributeSyncExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Class of objects to synchronize
     */
    public static final String ARG_CLASS_TO_SYNC = "syncClass";

    /**
     * CSV with IDs of objects to synchronize
     */
    public static final String ARG_OBJECTS_TO_SYNC = "syncObjects";

    /**
     * Source from which we're synchronizing.  Can be either 
     * LocalizedAttribute or Map.  If the source is LocalizedAttribute
     * we will assume that the specified LocalizedAttributes are authoritative
     * and copy them to the Maps.  If the source is Map we will assume that
     * the Maps are authoritative and update the LocalizedAttributes accordingly.
     */
    public static final String ARG_SYNC_SOURCE = "syncSource";
    
    /**
     * Number of attributes
     */
    public static final String ARG_BATCH_SIZE = "batchSize";
    
    public static final String SYNC_SOURCE_LOCALIZED_ATTRIBUTE = "LocalizedAttribute";
    public static final String SYNC_SOURCE_MAP = "Map";

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments from the caller
    //
    //////////////////////////////////////////////////////////////////////

    TaskResult _result;

    private boolean _trace;
    private String _classToSync;
    private List<String> _objectsToSync;
    private String _syncSource;

    //////////////////////////////////////////////////////////////////////
    //
    // Runtime state
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Thread safe task monitor.    
     */
    private TaskMonitor _monitor;

    /**
     * Flag set in another thread to halt the execution of
     * the refreshIdentityScores() method.
     */
    private boolean _terminate;

    private int _batchSize;
    
    /**
     * Enables profiling messages.
     * This is only used during debugging, it can't be set
     * from the outside.
     */
    private boolean _profile;
    
    //
    // Statistics
    //

    private long _taskCompleted;
    private long _taskTotalToSync;

    // 
    // Performance testing
    // 
    Date _blockStart;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public LocalizedAttributeSyncExecutor() {
    }

    public boolean terminate() {

        _terminate = true;

        return true;
    }

    private void trace(String msg) {
        log.info(msg);
        if (_trace)
            System.out.println(msg);
    }
    
    
    /**
     * For threading purposes we moved to a local copy of 
     * TaskMonitor that's synchronized and has a refresh
     * method.
     * 
     * Allow this to be configured through the normal
     * setMonitor method and if its a TaskMonitor
     * allow the one set on the executor to be 
     * used. 
     * 
     * We are now calling tasks from upgrade process
     * where the monitors need to write to the
     * upgrader data and not to the task result.
     * 
     */
    private void configureMonitor(SailPointContext context, TaskResult result) {
        Monitor baseMonitor = getMonitor();
        if ( baseMonitor != null ) {
            if ( baseMonitor instanceof TaskMonitor ) {
                _monitor = (TaskMonitor)baseMonitor;
            }
        }
        // this is now thread safe
        if ( _monitor == null ) {
            _monitor = new TaskMonitor(context, result);
        }
    }

    public void execute(SailPointContext context, 
                        TaskSchedule sched, 
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        if (context == null)
            throw new GeneralException("Unspecified context");

        configureMonitor(context, result);

        Meter.reset();
        Meter.enterByName("Prepare LocalizedAttributeSync");

        _result = result;
        _trace = args.getBoolean(ARG_TRACE);
        _classToSync = args.getString(ARG_CLASS_TO_SYNC);
        _objectsToSync = args.getStringList(ARG_OBJECTS_TO_SYNC);
        _syncSource = args.getString(ARG_SYNC_SOURCE);
        _profile = args.getBoolean(ARG_PROFILE);
        _batchSize = args.getInt(ARG_BATCH_SIZE);
        if (_batchSize < 1) {
            _batchSize = 100;
        }
        _blockStart = new Date();
        _taskCompleted = 0;

        Meter.exitByName("Prepare LocalizedAttributeSync");

        try {
            List<Class> classesToSync = getClassesToSync(_classToSync);
            // Synch from LocalizedAttributes to Maps
            if (!Util.isNullOrEmpty(_syncSource) && _syncSource.equalsIgnoreCase(SYNC_SOURCE_LOCALIZED_ATTRIBUTE)) {
                Meter.enterByName("Copying LocalizedAttributes into Maps");
                
                // Set the overall count across all specified classes
                Iterator<BigInteger> countResult;
                
                if (Util.isEmpty(classesToSync)) {
                    countResult = null;
                } else if (classesToSync.size() == 1) {
                    // We want to count the number of objects that are about to be updated, but the table doesn't hold a hibernate
                    // reference to those objects.  For that reason we have to fall back on a raw SQL query rather than going through
                    // a QueryOptions object
                    countResult = context.search("sql:select count(distinct target_id) from " + 
                            BrandingServiceFactory.getService().brandTableName("spt_localized_attribute") + 
                            " where target_class = '" + classesToSync.get(0).getSimpleName() + "' and attribute = 'description'", 
                            null, new QueryOptions());
                } else {
                    // We want to count the number of objects that are about to be updated, but the table doesn't hold a hibernate
                    // reference to those objects.  For that reason we have to fall back on a raw SQL query rather than going through
                    // a QueryOptions object
                    countResult = context.search("sql:select count(distinct target_id) from " + 
                            BrandingServiceFactory.getService().brandTableName("spt_localized_attribute") + 
                            " where target_class in ('" + getSimpleNamesAsCsv(classesToSync) + "') and attribute = 'description'", 
                            null, new QueryOptions());                    
                }
                                
                if (countResult == null) {
                    // Should never happen
                    _taskTotalToSync = 0;
                } else {
                    _taskTotalToSync = Util.otoi(countResult.next());
                }

                for (Class classToSync : classesToSync) {
                    copyLocalizedAttributesToDescriptionMaps(context, classToSync);
                }
                
                Meter.exitByName("Copying LocalizedAttributes into Maps");
            } else {
                // Synch from Maps to LocalizedAttributes
                Meter.enterByName("Copying Maps to LocalizedAttributes");

                if (classesToSync.size() == 1 && !Util.isEmpty(_objectsToSync)) {
                    Class classToSync = classesToSync.get(0);
                    // If only a single class was specified then we support the ability to sync individual objects
                    if (_objectsToSync.size() == 1) {
                        // For a single object use the light-weight method
                        String targetName = _objectsToSync.get(0);
                        if (!Util.isNullOrEmpty(targetName)) {
                            Describable objToSync = (Describable) context.getObjectById(classToSync, targetName);
                            Describer describer = new Describer(objToSync);
                            describer.saveLocalizedAttributes(context);
                            context.commitTransaction();
                            context.decache((SailPointObject)objToSync);
                            _taskCompleted++;
                        }
                    } else {
                        // For multiple objects use the more complex API call
                        copyDescriptionMapsToLocalizedAttributes(context, classToSync, _objectsToSync);
                    }
                } else {
                    // Otherwise sync everything
                    for (Class classToSync : classesToSync) {
                        copyDescriptionMapsToLocalizedAttributes(context, classToSync, null);
                    }
                }            
                
                
                Meter.exitByName("Copying Maps to LocalizedAttributes");
            }

            String progress = "LocalizedAttribute synchronization complete";
            trace(progress);
            _monitor.updateProgress(progress); 

            if (_terminate) {
                result.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
                result.setTerminated(true);
            }

            // final meter report
            if (_profile) {
                Meter.report();
            }
        } catch (Exception e) {
            log.error("The LocalizedAttribute synchronization task failed", e);
            throw e;
        } finally {
            trace(Util.ltoa(_taskCompleted) + " total desciptions synchronized.");
            result.setAttribute(RET_TOTAL, Util.ltoa(_taskCompleted));
        }

    }

    protected void printProgress() {
        if ( log.isInfoEnabled() ) {
            try {
                if ( ( _taskCompleted % 1000 ) == 0 ) {
                    log.info("Processed [" + _taskCompleted + "] "
                               + Util.computeDifference(_blockStart,
                                                        new Date()));
                    _blockStart = new Date();
                }
            } catch(GeneralException e) {
                log.error(e);
            }
        }
    }

    private List<Class> getClassesToSync(String classArg) {
        List<Class> classesToSync = new ArrayList<Class>();
        
        try {
            if (Util.isNullOrEmpty(classArg)) {
                // In this case all classes will be synchronized 
                classesToSync = Arrays.asList(new Class[] {Bundle.class, Policy.class, Application.class});
            } else {
                // Only the specified class will be synchronized.  
                // Support either simple or fully-qualified class names.
                if (classArg.startsWith("sailpoint.object.")) {
                    classesToSync.add(Class.forName(classArg));                    
                } else {
                    classesToSync.add(Class.forName("sailpoint.object." + classArg));
                }
            }
        } catch (ClassNotFoundException e) {
            log.error(classArg + " is not a valid class to synchronize.  Ignoring the argument.", e);
        }
                
        return classesToSync;
    }
    
    
    /**
     * Persists the descriptions of the specified targets to LocalizedAttributes
     * Note that if the list of targets has more than 100 elements this method will force a decache on the context
     * @param context SailPointContext in which this operation is being performed
     * @param targetCls Class of the Describable objects being persisted
     * @param targets Reference to the objects being persisted
     * @return number of objects whose descriptions were copied to LocalizedAttributes
     */
    private int copyDescriptionMapsToLocalizedAttributes(SailPointContext context, Class<? extends SailPointObject> targetCls, List<String> targets) {
        int syncedTargets = 0;
        if (targetCls == null) {
            log.warn("An attempt was made to synchronize descriptions for a null class.  The call is being ignored.", new GeneralException("An attempt was made to copy localized attributes for a null class.  The call is being ignored."));
        } else {
            try {
                QueryOptions targetsQuery = new QueryOptions();
                if (!Util.isEmpty(targets)) {
                    targetsQuery.add(Filter.in("id", targets));
                }
                int numTargetsToCopy = context.countObjects(targetCls, targetsQuery);
                _monitor.updateProgress(new Message(MessageKeys.TASK_SYNC_DESCRIPTION_COPYING, numTargetsToCopy, targetCls.getSimpleName()).getLocalizedMessage(), 0);
                int remainingTargetsToCopy = numTargetsToCopy;
                // Track the start and end of the current sublist so that we know what
                // to commit in the current loop as well as what is remaining to clean
                // up at the end.  In other words, the startOfSublist and endOfSublist
                // variables are tracking the start and end of the current batch, and 
                // the batch is in terms of the number of objects being updated.
                int startOfSublist = 0;
                int endOfSublist = _batchSize;
                while (remainingTargetsToCopy > _batchSize && !_terminate) {
                    // Break the targets into batches so we don't hog resources
                    List<String> currentTargetIds;
                    if (Util.isEmpty(targets)) {
                        // It's admittedly wasteful to fetch just the IDs now so that 
                        // we can fetch full objects later, but the alternative was
                        // to use an entirely different code path to process the unknown
                        // targets in batches
                        currentTargetIds = new ArrayList<String>();
                        targetsQuery.setResultLimit(_batchSize);
                        targetsQuery.setFirstRow(startOfSublist);
                        currentTargetIds = getTargetsInBatch(context, targetCls, targetsQuery);
                    } else {
                        currentTargetIds = targets.subList(startOfSublist, endOfSublist); 
                    }
                    
                    saveLocalizedAttributesForTargets(context, targetCls, currentTargetIds, true, true);
                    startOfSublist += _batchSize;
                    endOfSublist += _batchSize;
                    syncedTargets += _batchSize;
                    remainingTargetsToCopy -= _batchSize;
                    _taskCompleted += _batchSize;
                    _monitor.updateProgress(null, getPercentComplete());
                }
                                
                if (remainingTargetsToCopy > 0 && !_terminate) {
                    if (Util.isEmpty(targets)) {
                        targetsQuery.setFirstRow(startOfSublist);
                        targets = getTargetsInBatch(context, targetCls, targetsQuery);
                        startOfSublist = 0;
                        endOfSublist = targets.size();
                    } else {
                        if (endOfSublist > targets.size()) {
                            endOfSublist = targets.size();
                        }                    
                    }
                    
                    List<String> currentTargets = targets.subList(startOfSublist, endOfSublist);
                    saveLocalizedAttributesForTargets(context, targetCls, currentTargets, true, false);
                    syncedTargets += remainingTargetsToCopy;
                    _taskCompleted += remainingTargetsToCopy;
                    _monitor.updateProgress(null, getPercentComplete());
                }
            } catch (GeneralException e) {
                log.error("Failed to upgrade localized attributes", e);
                
            } catch (IllegalArgumentException e) {
                log.error("Failed to upgrade localized attributes", e);
            }
        }
        
        return syncedTargets;
    }
    
    /**
     * Persists the descriptions of the specified targets to LocalizedAttributes
     * Note that if the list of targets has more than 100 elements this method will force a decache on the context
     * @param context SailPointContext in which this operation is being performed
     * @param targetCls Class of the Describable objects being persisted
     * @return number of objects whose descriptions were copied to LocalizedAttributes
     */
    private void copyLocalizedAttributesToDescriptionMaps( SailPointContext context, Class<? extends SailPointObject> targetCls) throws GeneralException {

        if (targetCls == null) {
            log.warn("An attempt was made to synchronize localized attributes for a null class.  The call is being ignored.", new GeneralException("An attempt was made to copy localized attributes for a null class.  The call is being ignored."));
        } else {
            // If _objectsToSync was specified we don't need to count and we have a special query
            QueryOptions descriptionsQuery;
            String simpleClassName = targetCls.getSimpleName();
            // This Set is used to log warnings regarding objects that were flagged for synchronization
            // but had no corresponding LocalizedAttributes
            Set<String> unsyncedObjects = new HashSet<String>();
            if (!Util.isEmpty(_objectsToSync)) {
                unsyncedObjects.addAll(_objectsToSync);
                descriptionsQuery = new QueryOptions(Filter.and(
                        Filter.eq("targetClass", simpleClassName), 
                        Filter.or(Filter.in("targetName", _objectsToSync), Filter.in("targetId", _objectsToSync)),
                        Filter.eq("attribute", "description")));
                descriptionsQuery.addOrdering("targetId", true);
            } else {
                descriptionsQuery = new QueryOptions(Filter.and(Filter.eq("targetClass", targetCls.getSimpleName()), Filter.eq("attribute", "description")));
                descriptionsQuery.addOrdering("targetId", true);
            }
            
            int numAttributesToCopy = context.countObjects(LocalizedAttribute.class, descriptionsQuery);
            if (numAttributesToCopy == 0) {
                if (!unsyncedObjects.isEmpty()) {
                    _monitor.getTaskResult().addMessage(new Message(Type.Warn, MessageKeys.TASK_SYNC_DESCRIPTION_NOT_FOUND, Util.setToCsv(unsyncedObjects)));
                }
                return;
            }
            
            log(new Message(MessageKeys.TASK_SYNC_DESCRIPTION_COPYING_LOCALIZED_ATTRIBUTE, numAttributesToCopy, _taskTotalToSync, targetCls.getSimpleName()).getLocalizedMessage());
            
            try {
                descriptionsQuery.setCloneResults(true);
                Iterator<Object[]> descriptionsToCopy = context.search(LocalizedAttribute.class, descriptionsQuery, "targetId,targetName,locale,value");
                Map<String, Map<String, String>> descriptionMaps = new HashMap<String, Map<String, String>>();

                while (descriptionsToCopy.hasNext() && !_terminate) {
                    Object[] descriptionInfo = descriptionsToCopy.next();
                    String targetId = (String) descriptionInfo[0];
                    String targetName = (String) descriptionInfo[1];                    
                    String locale = (String) descriptionInfo[2];
                    String value = (String) descriptionInfo[3];
                    if (Util.isNullOrEmpty(targetId) || Util.isNullOrEmpty(locale)) {
                        log.warn("Cannot sychronize Localized Attribute with targetId " + targetId + " and locale " + locale + ".");
                    } else {
                        // info("Processing description for object with id {0} on locale {1} with a value of {2} ", targetId, locale, value);
                        // Figure out if it's time to flush
                        if (descriptionMaps.size() % _batchSize == 0 && !descriptionMaps.isEmpty() && !descriptionMaps.containsKey(targetId)) {
                            // Our map is full and the next object in line doesn't match anything that we already have so assume 
                            // that it's time to flush the maps
                            // Note that this will clear the description map
                            flushMaps(context, targetCls, descriptionMaps);
                        }
                        
                        Map<String, String> descriptionMap = descriptionMaps.get(targetId);
                        if (descriptionMap == null) {
                            descriptionMap = new HashMap<String, String>();
                            descriptionMaps.put(targetId, descriptionMap);
                        }
                        descriptionMap.put(locale, value);
                        unsyncedObjects.remove(targetId);
                        unsyncedObjects.remove(targetName);
                    }
                }
                
                // Save the remaining descriptions
                int remaining = descriptionMaps.size(); 
                if (remaining > 0) {
                    flushMaps(context, targetCls, descriptionMaps);
                }
                
                if (!unsyncedObjects.isEmpty()) {
                    _monitor.getTaskResult().addMessage(new Message(Type.Warn, MessageKeys.TASK_SYNC_DESCRIPTION_NOT_FOUND, Util.setToCsv(unsyncedObjects)));
                }
                
            } catch (GeneralException e) {
                log.error("Failed to upgrade localized attributes", e);
                
            } catch (IllegalArgumentException e) {
                log.error("Failed to upgrade localized attributes", e);
            }
        }
    }
    
    private String getSimpleNamesAsCsv(List<Class> classesToSync) {
        List<String> simpleNames = new ArrayList<String>();
        if (!Util.isEmpty(classesToSync)) {
            for (Class classToSync : classesToSync) {
                simpleNames.add(classToSync.getSimpleName());
            }
        }
        return Util.listToCsv(simpleNames, true);
    }

    private int flushMaps(SailPointContext context, Class<? extends SailPointObject> targetCls, Map<String, Map<String, String>> descriptionMaps) throws GeneralException {
        Set<String> targetIds = descriptionMaps.keySet();
        int syncedObjs = 0;
        // info("Attempting to flush {0} objects of type {1}.", targetIds.size(), targetCls.getName());
        
        if (Describable.class.isAssignableFrom(targetCls)) {
            QueryOptions queryOptions = new QueryOptions(Filter.in("id", targetIds));
            List<? extends SailPointObject> objs = context.getObjects(targetCls, queryOptions);
            for (SailPointObject obj : objs) {
                if (obj != null) { 
                    Describable describableObj = (Describable) obj;
                    Describer describer = new Describer(describableObj);
                    describer.addDescriptions(descriptionMaps.get(obj.getId()));
                    context.saveObject(obj);
                    _taskCompleted++;
                    syncedObjs++;
                    _monitor.updateProgress(null, getPercentComplete());
                }
            }
            context.commitTransaction();
            context.decache();
            descriptionMaps.clear();
        } else {
            String msg = "Can not upgrade descriptions for objects of type " + targetCls.getName() + " because they do not implement sailpoint.object.Describable";
            throw new GeneralException(msg);
        }
        
        return syncedObjs;
    }
    
    private void saveLocalizedAttributesForTargets(SailPointContext context, Class<? extends SailPointObject> targetCls, List<String> currentTargets, boolean commit, boolean decache) throws GeneralException {
        QueryOptions targetsQuery = new QueryOptions(Filter.or(Filter.in("id", currentTargets), Filter.in("name", currentTargets)));
        List<SailPointObject> objectsToCopy = (List<SailPointObject>)context.getObjects(targetCls, targetsQuery);
        if (Util.isEmpty(objectsToCopy)) {
            log.warn("An attempt was made to synchronize descriptions for a list of obsolete references.  The request was disregarded.", new GeneralException("An attempt was made to synchronize descriptions for a list of obsolete references.  The request was disregarded."));
        } else {
            for (SailPointObject objectToCopy : objectsToCopy) {
                Describer describer = new Describer((Describable) objectToCopy);
                describer.saveLocalizedAttributes(context);
            }
            if (commit) {
                context.commitTransaction();
                if (decache) {
                    context.decache();
                }
            }
            _monitor.updateProgress(null, getPercentComplete());
        }

    }
    
    private List<String> getTargetsInBatch(SailPointContext context, Class<? extends SailPointObject> targetCls, QueryOptions targetsQuery) throws GeneralException {
        List<String> targets = new ArrayList<String>();
        Iterator<Object[]> targetsInBatch = context.search(targetCls, targetsQuery, "id");
        if (!Util.isEmpty(targetsInBatch)) {
            while (targetsInBatch.hasNext()) {
                String target = (String) targetsInBatch.next()[0];
                if (!Util.isNullOrEmpty(target)) {
                    targets.add(target);
                }
            }
        }
        return targets;
    }
    
    private int getPercentComplete() {
        long percentComplete;
        if (_taskTotalToSync == 0) {
            percentComplete = 100;
        } else {
            percentComplete = (_taskCompleted * 100) / _taskTotalToSync;
        }
        return (int)percentComplete;
    }
    
    private void log (String msg) {
        log.info(msg);
    }

}
