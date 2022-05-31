/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.activity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.ActivityFieldMap;
import sailpoint.object.AllowableActivity;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.object.ActivityFieldMap.ActivityField;
import sailpoint.object.ActivityFieldMap.TransformationType;
import sailpoint.object.ApplicationActivity.Action;
import sailpoint.object.ApplicationActivity.Result;
import sailpoint.connector.CollectorServices;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * Abstract base class that can be used when writting ActivityCollectors.
 * It provides some base functionality for handling configuration attributes,
 * filtering, activity field transformations and position config handling.
 * 
 */
public abstract class AbstractActivityCollector extends CollectorServices 
                                                implements ActivityCollector {

    private static Log log = 
        LogFactory.getLog(AbstractActivityCollector.class);

    ///////////////////////////////////////////////////////////////////////////
    //
    // 
    //
    ///////////////////////////////////////////////////////////////////////////

    public static final String CONFIG_FIELD_MAP = "fieldMap";
    public static final String CONFIG_TRANSFORMATION_RULE_INFO = 
        "Name of the rule used to transform native WindowsEventLogEntries into ApplicationActivities.";
    public static final String CONFIG_BLOCKSIZE ="blockSize";
 
    /** 
     * The ActivityDatSource object, which holds the configuration for 
     * an implementation. 
     */
    private ActivityDataSource _dataSource;

    /**
     * A cached copy of the global rule if existed to prevent
     * getting the rule for each row. This Rule is run after
     * all other transformations and should return and 
     * activity object.
     */
    private Rule _globalRule;

    /**
     *  Object that can hold the last position that
     *  was processed.  The correlator will ask for this
     *  after completed and store it depending on the policy
     *  of the aggregation scan. 
     */
    protected Map<String,Object> _positionConfig;

    /**
     * Cached map of the activities we will allow through
     * to the aggregator. Optimization..
     */
    protected Map<Action, AllowableActivity> _allowable;

    /**
     * Creates new instance with the given datasource
     *
     * @param dataSource datasource for the collector
     */
    public AbstractActivityCollector(ActivityDataSource dataSource) {
        super(dataSource.getConfiguration());
        setDataSource(dataSource);
    }

    /**
     * Set the application associated with this object.
     * @param dataSource datasource for the collector
     */
    public void setDataSource(ActivityDataSource dataSource) {
        _dataSource = dataSource;
    }

    /**
     * Returns the datasource associated with this object.
     */
    public ActivityDataSource getDataSource() {
        return _dataSource;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Default Filtering
    //
    ///////////////////////////////////////////////////////////////////////////

    public void setPositionConfig(Map<String,Object> config) {
        _positionConfig = config;
    }

    public Map<String,Object> getPositionConfig() {
        if ( _positionConfig == null ) {
            _positionConfig = new HashMap<String,Object>();
        }
        return _positionConfig;
    }

    /*
     * Get allowable activities defined for this data source. 
     */
    @SuppressWarnings("unchecked")
    private Map<Action, AllowableActivity> getAllowable() {
        if ( _allowable == null ) {
            List<AllowableActivity> allowable=getListAttribute(CONFIG_FILTERS);
            if ( ( allowable != null ) && ( allowable.size() > 0 ) ) {
                _allowable = new HashMap<Action, AllowableActivity>();
                for ( AllowableActivity allow : allowable ) {
                    Action action = allow.getAction();
                    _allowable.put(action, allow);
                }
            }
        }
        return _allowable;
    }

    /**
     * Check the incomming activity against the defined allowable
     * activities. If there is a configuration setting named
     * this.CONFIG_ALLOW_ALL, then filtering is disabled
     * and all events will make it through the filter.
     *
     * Otherwise if filtering is enabled, only activities that
     * match the configured AllowableActivities will be 
     * allowed through the filter.  The match can happen in 
     * 
     * NOTE: We don't have many requirements here, so this is likely
     * to be revisited. This is fairly redumentry and we can get 
     * more complext by adding both deny and allow lists, or 
     * turn to rules and let the rules decide if the activity
     * should be allowed.
     */
    public boolean filter(ApplicationActivity activity) {

        boolean filter  = true;

        Map<Action, AllowableActivity> allowable = getAllowable();

        // flag if set will allow ALL events
        String allowAll = getStringAttribute(CONFIG_ALLOW_ALL);
        if ( allowAll == null && allowable == null || Util.otob(allowAll) ) {        
            // by default allow all types of records.
            return false;
        }

        if ( allowable != null ) {
            Action action = activity.getAction();
            AllowableActivity allow = allowable.get(action);
            if ( allow != null ) {
                filter = checkActivity(allow, activity);
            }
        }
        return filter;
    }

    // Think about two cases:
    // Or can null be used here?
    // ALL results *
    // ALL targets *
    private boolean checkActivity(AllowableActivity allow, 
                                  ApplicationActivity activity ) {

        List<Result> results = allow.getResults();
        if ( ( results != null ) && ( results.size() > 0 )  ) {
            Result thisResult = activity.getResult();
            // should this be turned into a HashSet?
            if ( results.contains(thisResult) ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Allowing activity based on result." 
                               + toXml(activity) + " allow: " 
                               + toXml(allow));
                }
                return false;
            } else { 
                if ( log.isDebugEnabled() ) {
                    log.debug("Filtering activity based on activity." 
                               + toXml(activity) + " allow: " 
                               + toXml(allow));
                }
            }
        }

        List<String> targets = allow.getTargets();
        if ( ( targets != null ) && ( targets.size() > 0 )  ) {
            String thisTarget = activity.getTarget();
            // should this be turned into a HashSet?
            if ( targets.contains(thisTarget) ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Allowing activity based on target." 
                               + toXml(activity) + " allow: " 
                               + toXml(allow));
                }
                return false;
            } else {
                if ( log.isDebugEnabled() ) {
                    log.debug("Filtering activity based on target." 
                               + toXml(activity) + " allow: " 
                               + toXml(allow));
                }
            }             
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Transformations
    //
    ///////////////////////////////////////////////////////////////////////////

    protected Object transformValue(SailPointContext sp,
                                    Map<String,Object> context, 
                                    ActivityFieldMap fieldMap) 
        throws GeneralException {

        return transformValue(sp, context, context, fieldMap);
    }

    /**
     * Use the fieldMap to transforme the value, allow different 
     * maps for fieldMap resolution and another for the rule 
     * context.
     */
    protected Object transformValue(SailPointContext sp,
                                    Map<String,Object> fieldMapContext, 
                                    Map<String,Object> context, 
                                    ActivityFieldMap fieldMap) 
        throws GeneralException {

        Object obj = null;

        try {

            String token = null;
            String mapsTo = fieldMap.getSource();
            if ( mapsTo != null ) {
                token = (String)fieldMapContext.get(mapsTo);
            }

            TransformationType type = fieldMap.getTransformationType();
            if (log.isDebugEnabled())
                log.debug("TransformationType: " + type);

            if ( type.equals(TransformationType.None) ) {
                obj = token; 
            } else
            if ( type.equals(TransformationType.DateConverter) ) {
                String dateFormat = fieldMap.getDateFormat();

                if ( dateFormat == null )
                   throw new GeneralException("Type DateConverted but " 
                      + "does not have a specified dateFormat");

                SimpleDateFormat f = new SimpleDateFormat(dateFormat);
                Date date = null;
                if ( token != null ) {
                    date = f.parse(token);
                    String tz = fieldMap.getTimeZone();
                    if ( tz != null ) {
                        if (log.isDebugEnabled())
                            log.debug("TimeZone specified: " + tz );
                        
                        TimeZone zone = TimeZone.getTimeZone(tz);
                        if ( zone != null) {
                            GregorianCalendar cal =
                                (GregorianCalendar) GregorianCalendar.getInstance();
                            cal.setTime(date);
                            cal.setTimeZone(zone);
                            date = cal.getTime();
                        }
                    }
                }
                obj = date;
            } else
            if ( type.equals(TransformationType.Rule) ) {
                if ( sp != null ) {
                    Rule rule = fieldMap.getRule();
                    if ( rule != null ) {
                        obj = sp.runRule(rule, context);
                    } else {
                        throw new GeneralException("Attempting to transform " 
                               + " using a rule, but" 
                               + " there wasn't a rule defined for this field."
                               + " fieldName: " + fieldMap.getField() 
                               + " source: " + fieldMap.getSource());
                    }
                } else {
                    log.debug("Context was null, unable to run Rule.");
                }
            } else
            if ( type.equals(TransformationType.Script) ) {
                if ( sp != null ) {
                    Script script = fieldMap.getScript();
                    if (script != null)
                        obj = sp.runScript(script, context);
                } else {
                    log.debug("Context was null, unable to run Rule.");
                }
            }
        } catch (java.text.ParseException e ) {
             throw new GeneralException(e);
        }
        return obj;
    }

    public ApplicationActivity buildActivity( SailPointContext context,
                                              List<ActivityFieldMap> fieldMap,
                                              Map<String,Object> ruleContext)
        throws GeneralException {

        return buildActivity(context, fieldMap, ruleContext, null );
    }

    public ApplicationActivity buildActivity( SailPointContext context,
                                              List<ActivityFieldMap> fieldMap,
                                              Map<String,Object> ruleContext,
                                              ApplicationActivity activity)
        throws GeneralException {

        if ( activity == null ) {
            activity = new ApplicationActivity();
        }

        Map<String,Object> ruleCtx = new HashMap<String,Object>();
        ruleCtx.putAll(ruleContext);

        if ( fieldMap != null ) {
            processFieldMap(context, fieldMap, activity, ruleCtx);
        }
        // See if there is a transformation script configured which can
        // change any field on the current activity object. 
        // Do this last so we can mix and match the other 
        // transformations.
        Rule globalRule = getGlobalRule(context);
        if ( globalRule != null ) {
            if (log.isDebugEnabled())
                log.debug("Running Global Rule." + globalRule.getName());
            
            ruleCtx.put("activity", activity);
            activity =(ApplicationActivity)context.runRule(globalRule,ruleCtx);
        }
        return activity;
    }

    private void processFieldMap(SailPointContext context,
                                 List<ActivityFieldMap> fieldMap, 
                                 ApplicationActivity activity, 
                                 Map<String,Object> ruleCtx) 
        throws GeneralException {

        for ( ActivityFieldMap map : fieldMap ) {
            ActivityField field = map.getField();

            if ( field == null ) {
                // Shouldn't happen... but guard against it..
                continue;
            }

            Object value = transformValue(context, ruleCtx, map );
            if ( value == null ) {
                // TODO: ignore now, revisit maybe this is an error?
                continue;
            }

            if ( field.compareTo(ActivityField.SP_TimeStamp) == 0  ) {
                if ( value instanceof Date ) {
                    Date dateValue = (Date)value; 
                    activity.setTimeStamp(dateValue);
                } else {
                    throw new GeneralException("Field SP_TimeStamp needs" 
                          + " to be a Date java type.");
                }
            } else
            if ( field.compareTo(ActivityField.SP_NativeUserId) == 0  ) {
                activity.setUser(coerceToString(value)); 
            } else
            if ( field.compareTo(ActivityField.SP_Info) == 0  ) {
                activity.setInfo(coerceToString(value)); 
            } else
            if ( field.compareTo(ActivityField.SP_Target) == 0  ) {
                activity.setTarget(coerceToString(value)); 
            } else
            if ( field.compareTo(ActivityField.SP_Action) == 0  ) {
                if ( value != null) {
                    Action action = resolveAction(value);
                    activity.setAction(action);
                }
            } else
            if ( field.compareTo(ActivityField.SP_Result) == 0  ) {
                if ( value != null) {
                    Result result = resolveResult(value);
                    activity.setResult(result);
                }
            }
        }
    }

    /** 
     * Method that resolves an action given a String or an Action object.
     */
    protected Action resolveAction(Object value) {
        Action action = null;
        if ( value == null ) return action;

        if ( value instanceof Action ) {
            action = (ApplicationActivity.Action)value;
        } else
        if ( value instanceof String ) {
            action = Action.valueOf((String)value);
        }
        return action; 
    }

    /** 
     * Method that resolves a Result given a String or a Result object;
     */
    protected Result resolveResult(Object value) {
        ApplicationActivity.Result result = null;
        if ( value == null ) return result;

        if ( value instanceof Result ) {
            result = (Result)value;
        } else
        if ( value instanceof String ) {
            result = Result.valueOf((String)value);
        }
        return result;
    }

    /**
     * Try to resolve the ActivityField from a string, don't
     * let exceptions during resolution out of this method and
     * just return null if the value is unresolvable.
     */ 
    protected ActivityField fieldFromString(String str) {
        ActivityField mapsTo = null;
        try {
            mapsTo = ActivityField.valueOf(str);
        } catch(java.lang.IllegalArgumentException e) {
            if (log.isDebugEnabled())
                log.debug("Could not map ["+str+"] to an activityfield");
        }
        return mapsTo;
    }

    /**
     * Avoid cast exceptions by turning returned rule values
     * we exspect to be String into Strings.
     */
    protected String coerceToString(Object value) { 
        String coercedValue = null;   
        if ( value != null ) {
            if ( value instanceof String ) 
                coercedValue = (String)value;
            else
                coercedValue = value.toString();
        }
        return coercedValue;
    }

    public Rule getGlobalRule(SailPointContext ctx) throws GeneralException {
        if ( _globalRule == null && _dataSource != null) {             
             _globalRule = _dataSource.getTransformationRule();
        }
        return _globalRule;
    }

    public SailPointContext getSailPointContext() throws GeneralException {
         return SailPointFactory.getCurrentContext();
    }

    /**
     * Cleans up the configuration so that it only contains attributes known to the default configuration
     * that are not also found in the additionalInvalidAttributeNames
     * @param config
     * @param additionalInvalidAttributeNames
     */
    public void cleanUpConfig(Attributes<String,Object> config, Set<String> additionalInvalidAttributeNames) {
        if (additionalInvalidAttributeNames == null) {
            additionalInvalidAttributeNames = new HashSet<String>();
        }

        List<AttributeDefinition> validAttributes = getDefaultConfiguration();
        Set<String> validAttributeNames = new HashSet<String>();

        for (AttributeDefinition def : validAttributes) {
            validAttributeNames.add(def.getName());
        }

        Set<String> attributesInConfig = config.keySet();
        // Copy the Set of keys into an array to avoid ConcurrentModificationExceptions on the Map
        String[] arrayOfAttributesInConfig = attributesInConfig.toArray(new String[attributesInConfig.size()]);

        for (int i = 0; i < arrayOfAttributesInConfig.length; ++i) {
            if (!validAttributeNames.contains(arrayOfAttributesInConfig[i]) ||
                additionalInvalidAttributeNames.contains(arrayOfAttributesInConfig[i])) {
                config.remove(arrayOfAttributesInConfig[i]);
            }
        }
    }

    public void cleanUpConfig(Attributes<String,Object> config) {
        cleanUpConfig(config, null);
    }
}
