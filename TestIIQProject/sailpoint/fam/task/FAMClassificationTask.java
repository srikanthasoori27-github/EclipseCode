/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.classification.ClassificationResult;
import sailpoint.fam.FAMService;
import sailpoint.fam.service.FAMClassificationService;
import sailpoint.object.Attributes;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FAMClassificationTask extends AbstractTaskExecutor {

    private static Log _log = LogFactory.getLog(FAMClassificationTask.class);

    TaskMonitor _monitor;
    Attributes<String, Object> _args;
    SailPointContext _context;
    FAMService _service;
    Rule _classificationCustomizationRule;
    Rule _classificationFilterRule;
    String _descriptionLocale;

    boolean _terminate;


    /**
     * METRICS
      */
    int _classificationsCreated;
    //Set containing the id's of ManagedAttributes processed
    Set<String> _groupsProcessed;
    //Number of Classification processed
    int _classificationsProcessed;
    //Number of uncorrelated classifications
    int _uncorrelatedAssociations;
    //Number of Classification Associations Processed
    // NOTE: These are actually Permissions within FAM, but we propogate the Classifications to the Group in IIQ
    int _associationsProcessed;


    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws Exception {

        _monitor = new TaskMonitor(context, result);
        _args = args;
        _context = context;
        _service = new FAMService(_context);

        init();

        //CleanUp previous agg'd classifications
        phaseCleanup();

        //Aggregate current classifications
        phaseAggregate();

        //Calculate metrics
        phaseSaveResults();


    }

    //Rule used to customize the Classification Objects
    static String CLASSIFICATION_CUSTOMIZATION_RULE = "classificationCustomizationRule";
    //Rule used to add filters to the Classification Fetching
    static String CLASSIFICATION_FILTER_RULE = "classificationFilterRule";

    /**
     * An argument used to request that the native classification description be promoted into the
     * IIQ Classification description list.  The value of the
     * attribute must be a valid locale identifier that is on
     * the supportedLanguages list in the system configuration.
     */
    private static final String ARG_DESCRIPTION_LOCALE = "descriptionLocale";

    private void init() throws GeneralException {

        if (_args.containsKey(CLASSIFICATION_CUSTOMIZATION_RULE)) {
            _classificationCustomizationRule = _context.getObjectByName(Rule.class,
                    _args.getString(CLASSIFICATION_CUSTOMIZATION_RULE));
            if (_classificationCustomizationRule != null) {
                //Go ahead and fully load in case of decache
                _classificationCustomizationRule.load();
            } else {
                _log.warn("Unable to find Rule name[" + _args.getString(CLASSIFICATION_CUSTOMIZATION_RULE) + "]");
            }
        }

        if (_args.containsKey(CLASSIFICATION_FILTER_RULE)) {
            _classificationFilterRule = _context.getObjectByName(Rule.class,
                    _args.getString(CLASSIFICATION_FILTER_RULE));
            if (_classificationFilterRule != null) {
                //Go ahead and fully load in case of decache
                _classificationFilterRule.load();
            } else {
                _log.warn("Unable to find Rule name[" + _args.getString(CLASSIFICATION_FILTER_RULE) + "]");
            }
        }

        _descriptionLocale = _args.getString(ARG_DESCRIPTION_LOCALE);

        _classificationsCreated = 0;
        _groupsProcessed = new HashSet<>();
        _classificationsProcessed = 0;
        _uncorrelatedAssociations = 0;
        _associationsProcessed = 0;

    }

    /**
     * Delete all Classification references from MA's
     * TODO: Do we need to update model, or is it ok to query and iterate?
     */
    protected void phaseCleanup() throws GeneralException {

        //Update Process
        _monitor.updateProgress("Begin Cleanup");

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("classifications.classification.origin", FAMClassificationService.FAM_MODULE_SOURCE));
        ops.setDistinct(true);
        IncrementalObjectIterator iter = new IncrementalObjectIterator(_context, ManagedAttribute.class, ops);

        int cnt = 0;
        while (iter.hasNext()) {
            boolean updated = false;
            ManagedAttribute att = (ManagedAttribute) iter.next();
            if (att != null) {
                if (_log.isInfoEnabled()) {
                    _log.info("Pruning Classifications from ManagedAttribute[" + att.getDisplayableName() + "]");
                }
                cnt++;
                List<ObjectClassification> classifications = att.getClassifications();
                if (!Util.isEmpty(classifications)) {
                    Iterator<ObjectClassification> classIt = classifications.iterator();
                    while (classIt.hasNext()) {
                        ObjectClassification c = classIt.next();
                        if (!c.isEffective() && Util.nullSafeEq(c.getClassification().getOrigin(), FAMClassificationService.FAM_MODULE_SOURCE)) {
                            //Remove FAM Module Classifications
                            classIt.remove();
                            updated = true;
                            if (_log.isInfoEnabled()) {
                                _log.info("Removing Classification[" + c.getName() + "] from ManagedAttribute[" + att.getDisplayableName() + "]");
                            }
                        }
                    }

                    if (updated) {
                        _context.saveObject(att);

                    }

                }
            }

            if (cnt % 10 == 0) {
                //Incremental commit/decache
                _context.commitTransaction();
                _context.decache();
            }

        }

        if (_log.isInfoEnabled()) {
            _log.info(cnt + " Classifications Removed");
        }

        _context.commitTransaction();
        _context.decache();


    }

    protected void phaseAggregate() throws GeneralException {

        _monitor.updateProgress("Beginning Phase Aggregate");

        //Hook to set query options
        QueryOptions ops = runClassificationFilterRule();

        Iterator<ClassificationResult> resultIter = _service.getClassifications(ops);

        while (resultIter.hasNext()) {

            if (_terminate) {
                break;
            }
            ClassificationResult res = resultIter.next();
            _associationsProcessed++;

            if (ManagedAttribute.class == res.getObjectType()) {
                //Get the MA
                ManagedAttribute att = _context.getObjectById(ManagedAttribute.class, res.getObjectId());

                if (att != null) {
                    _groupsProcessed.add(att.getId());
                    List<Classification> classifications = res.getClassifications();
                    for (Classification clas : Util.safeIterable(classifications)) {
                        Classification c = clas;
                        _classificationsProcessed++;

                        if (Util.isNotNullOrEmpty(clas.getName())) {
                            Classification currClass = _context.getObjectByName(Classification.class, clas.getName());
                            if (currClass != null) {
                                c = currClass;
                            }
                        }

                        //Set the source
                        c.setOrigin(FAMClassificationService.FAM_MODULE_SOURCE);

                        if (_classificationCustomizationRule != null) {
                            c = runClassificationModificationRule(c, att);
                        }


                        if (c != null) {
                            //Promote description to locale
                            if (_descriptionLocale != null && Util.isNotNullOrEmpty(c.getDescription()) &&
                                    c.getDescription(_descriptionLocale) == null) {

                                c.addDescription(_descriptionLocale, c.getDescription());
                            }

                            boolean added = att.addClassification(c, Source.Aggregation.name(), false);
                            //Cascade save, or implicit save?
                            if (added) {
                                if (_log.isInfoEnabled()) {
                                    _log.info("Adding Classification[" + c.getName() + "] to ManagedAttribute[" + att.getDisplayableName() + "]");
                                }
                            } else {
                                if (_log.isInfoEnabled()) {
                                    _log.info("Duplicate Classification [" + c.getName() + "] found for ManagedAttribute[" + att.getDisplayableName() + "]");
                                }
                            }

                            if (Util.isNullOrEmpty(c.getId())) {
                                _classificationsCreated++;
                                //Save the Classification. ObjectClassification won't cascade
                                _context.saveObject(c);
                                _context.commitTransaction();
                                if (_log.isInfoEnabled()) {
                                    _log.info("Classification Created[" + c.toXml() + "]");
                                }
                            }
                        }

                        //TODO: Need to save/decache within the classifications, or wait until entire att processed? -rap
                    }
                } else {
                    _log.error("Unable to find ManagedAttribute with id[" + res.getObjectId() + "]");
                }

                _context.saveObject(att);
                _context.commitTransaction();
                //Could likely postpone the decache, but not sure how large the Classification List can become
                _context.decache();

            } else {
                if (_log.isInfoEnabled()) {
                    _log.info("Unknown objectType found on ClassificationResult " + res);
                }
                _uncorrelatedAssociations++;
            }

        }

    }


    //////////////////////////////////////////////////////////////////////
    //
    // Results
    //
    //////////////////////////////////////////////////////////////////////

    public static final String RET_GROUPS_PROCESSED = "groupsProcessed";
    public static final String RET_CLASSIFICATIONS_CREATED = "classificationsCreated";
    public static final String RET_ASSOCIATIONS_PROCESSED = "associationsProcessed";
    public static final String RET_CLASSIFICATIONS_PROCESSED = "classificationsProcessed";
    public static final String RET_UNCORRELATED_CLASSIFICATIOSN = "uncorrelatedAssociations";
    protected void phaseSaveResults() {
        TaskResult res = _monitor.getTaskResult();
        if (res != null) {
            res.setInt(RET_GROUPS_PROCESSED, Util.size(_groupsProcessed));
            res.setInt(RET_CLASSIFICATIONS_CREATED, _classificationsCreated);
            res.setInt(RET_CLASSIFICATIONS_PROCESSED, _classificationsProcessed);
            res.setInt(RET_UNCORRELATED_CLASSIFICATIOSN, _uncorrelatedAssociations);
            res.setInt(RET_ASSOCIATIONS_PROCESSED, _associationsProcessed);
        }
    }

    // SailPointObject passed into the rule
    static String RULE_SP_OBJECT = "object";
    // Classification object passed into the rule
    static String RULE_CLASSIFICATION_OBJECT = "classification";

    private Classification runClassificationModificationRule(Classification clas, SailPointObject o)
        throws GeneralException {

        Classification c = null;

        Map<String, Object> ruleParams = new HashMap<>();
        ruleParams.put(RULE_SP_OBJECT, o);
        ruleParams.put(RULE_CLASSIFICATION_OBJECT, clas);

        if (_log.isDebugEnabled()) {
            _log.debug("Running ClassificationModification rule for Classification[" + clas.toXml() + "]");
        }
        Object result = _context.runRule(_classificationCustomizationRule, ruleParams);

        if (result instanceof Classification) {
            c = (Classification) result;
        } else {
            _log.warn("ClassificationModificationRule must return Classification object" + result);
            //Keep null, prevent processing
        }

        return c;
    }

    private QueryOptions runClassificationFilterRule() throws GeneralException {
        QueryOptions ops = null;
        if (_classificationFilterRule != null) {
            if (_log.isDebugEnabled()) {
                _log.debug("Running ClassificationFilterRule[" + _classificationFilterRule.getName() + "]");
            }
            //Any params?
            Object o = _context.runRule(_classificationFilterRule, null);
            if (o instanceof QueryOptions) {
                ops = (QueryOptions) o;
            } else {
                _log.warn("ClassificationFilterRule must return QueryOptions");
            }

        }

        return ops;
    }


    @Override
    public boolean terminate() {
        _terminate = true;
        return true;
    }
}
