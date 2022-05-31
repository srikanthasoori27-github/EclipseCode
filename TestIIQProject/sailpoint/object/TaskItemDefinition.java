/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object describing a background task.
 * The main thing this does is define the Executor class.
 *
 * Currently the executor is indirectly instantiated by Quartz
 * through the SailPointJobFactory and accesssed through
 * the a JobAdapter.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Message;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

public abstract class TaskItemDefinition extends SailPointObject
    implements Cloneable
{
    private static final long serialVersionUID = 8933531729819894953L;
    
    public static final String TASK_TYPE_SEARCH_NAME = "Search Report";
    public static final String TASK_SUB_TYPE_SEARCH = "Search";

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Types.  This includes all of the types from the classes that implement
     * this class.
     */
    @XMLClass(xmlname="DefinitionType")
    public static enum Type {

        // Common Types
        Generic(MessageKeys.TASK_ITEM_TYPE_GENERIC),
        System(MessageKeys.TASK_ITEM_TYPE_SYSTEM),

            // TaskDefinition Types
        AccountAggregation(MessageKeys.TASK_ITEM_TYPE_ACCT_AGGREGATION),
        AccountGroupAggregation(MessageKeys.TASK_ITEM_TYPE_ACCT_GRP_AGGREGATION),
        ActivityAggregation(MessageKeys.TASK_ITEM_TYPE_ACTIVITY_AGGREGATION),
        ActivityAlert(MessageKeys.TASK_ITEM_TYPE_ACTIVITY_ALERTS),
        TargetAggregation(MessageKeys.TASK_ITEM_TYPE_TARGET_AGGREGATION),
        Identity(MessageKeys.TASK_ITEM_TYPE_IDENTITY),
        Certification(MessageKeys.TASK_ITEM_TYPE_CERTIFICATION),
        Report(MessageKeys.TASK_ITEM_TYPE_REPORT),
        GridReport(MessageKeys.TASK_ITEM_TYPE_GRID_REPORT),
        Scoring(MessageKeys.TASK_ITEM_TYPE_SCORING),
        Workflow(MessageKeys.TASK_ITEM_TYPE_WORKFLOW),
        LCM(MessageKeys.TASK_ITEM_TYPE_LCM),
        CertificationRefresh(MessageKeys.TASK_ITEM_TYPE_CERTIFICATION_REFRESH),
        RoleMining(MessageKeys.TASK_ITEM_TYPE_ROLE_MINING),
        ITRoleMining(MessageKeys.TASK_ITEM_TYPE_IT_ROLE_MINING),
        LiveReport(MessageKeys.TASK_ITEM_TYPE_LIVE_REPORT),
        SynchronizeDescriptions(MessageKeys.TASK_ITEM_TYPE_SYNCHRONIZE_DESCRIPTION),
        Classification(MessageKeys.TASK_ITEM_TYPE_CLASSIFICATION),

        // RequestDefinition Types
        Email(MessageKeys.TASK_ITEM_TYPE_EMAIL),
        Remediation(MessageKeys.TASK_ITEM_TYPE_REMEDIATION),
        ARMRequest(MessageKeys.TASK_ITEM_TYPE_ARM_REQUEST), // deprecated
        RolePropagation(MessageKeys.TASK_ITEM_TYPE_ROLE_PROPAGATION),
        Event(MessageKeys.TASK_ITEM_TYPE_EVENT),

        // this is a strange one, it can be used as a Request type
        // but also as the TaskResult type for a partitioned task
        Partition(MessageKeys.TASK_ITEM_TYPE_PARTITION);

        private String messageKey;

        Type(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }

    }



    /**
     * Specifies how a task updates TaskResults, about the
     * execution progress while running.
     */
    @XMLClass(xmlname="ProgressMode")
    public static enum ProgressMode {
        // Executor doesn't update progress, value is the same as null
        None,
        // Executor will be periodically updating the progresString
        // property of the task result during execution.
        String,
        // Executor will be periodically updating the progress
        // AND percentageComplete properties of the task result
        // during execution.
        Percentage
    }

    /**
     * Default loss limit value.  A value of <= 0 means do not perform
     * any loss limiting
     */
    public static final int DEFAULT_LOSS_LIMIT = 0;

    /**
     * Specifies the number of accounts we will wait to be successfully aggregated
     * before placing their ids into restartInfo attribute
     */
    public static final String ARG_LOSS_LIMIT = "lossLimit";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Set if this task is a specialization of another.
     * Specializations (or you could think of them like subclasses)
     * may override any of the fields in the parent,
     * though typically they only provide a set of arguments.
     * At the point of execution, the argument maps are merged,
     * with arguments in specializations overriding arguments of
     * the same name in the parent definitions.
     */
    TaskItemDefinition _parent;

    /**
     * The task type for searches and filtering in the UI.
     */
    Type _type;
    
    /**
     * The task subtype for searches and filtering in the UI.
     */
    String _subType;
    
    /**
     * Name of the executor class.
     * 
     * @ignore
     * This is maintained as a String rather than a resolved Class
     * so that it can be saved without necessarily having the class on the
     * path.  Necessary when exchanging object XML among systems that
     * may not have the same custom task executors, or restoring old
     * XML archives.
     */
    String _executor;

    /**
     * Metadata describing the inputs and outputs of the task.
     */
    Signature _signature;

    /**
     * Arguments to the executor.
     * Currently the values must be strings in order to get
     * them into Quartz via the JobSchedule object.
     */
    Attributes<String,Object> _arguments;

    /**
     * True if this is designed to be a "template" definition.
     * Templates are not run directly, they are first cloned and the
     * clone fleshed out with launch arguments that match the
     * Signature.
     */
    boolean _template;

    /**
     * True if this definition should not be displayed in the task
     * launch UI.  This may be set if there are a set of custom pages
     * for managing the tasks.
     */
    boolean _hidden;

    /**
     * Specifies the length of time the results are kept before being
     * deleted.
     *
     * If the value is zero the results do not expire. If the number 
     * is positive, the unit is days. If the number is -1 it means
     * the result expires immediately. If the number is less than -2
     * the number is made positive and treated as a number of seconds.
     */
    int _resultExpiration;

    /**
     * Specifies an optional location to find the task form which will
     * be used to render the task arguments. This should be a form
     * relative to the $SPHOME/tasks directory and should look
     * something like the tasks/arguments.xhtml file.
     */
    String _formPath;

    /**
     * Mode to publish how the executor will show progress, if at all.
     */
    ProgressMode _progressMode;

    /**
     * Interval the task will update progress.
     */
    int _progressInterval;

    /**
     * Optional list of rights for restricting access to this task.
     */
    protected List<SPRight> _rights;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public TaskItemDefinition() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public TaskItemDefinition getParent() {
        return _parent;
    }
    
    public TaskItemDefinition getParentRoot() {
    	TaskItemDefinition parent = null;
    	if(getParent()==null) {
    		return parent;
    	} else {
    		TaskItemDefinition nextParent = getParent();
    		while(nextParent!=null) {
    			parent = nextParent;
    			nextParent = nextParent.getParent();
    		}
    	}
    	
    	return parent;
    }

    public void setParent(TaskItemDefinition def) {
        _parent = def;
    }

    @XMLProperty
    public void setType(Type type) {
        _type = type;
    }

    public Type getType() {
        return _type;
    }

    // !! crap, this will serialize as <Attributes> but we
    // want <Arguments>, not sure this is worth messing with
    // another serialization mode, or Attributes subclass, should
    // we just rename the property "attributes" and be done with it?

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Attributes<String,Object> args) {
        if (args != null)
            _arguments = args;
        else {
            // always keep an empty map for JSF
            // !! is this really necessary, its annoying for the XML
            // serialization
            _arguments = new Attributes<String,Object>();
        }
    }

    public Object getArgument(String name) {
        return (_arguments != null) ? _arguments.get(name) : null;
    }

    public void setArgument(String name, Object value) {

        if (_arguments == null)
            _arguments = new Attributes<String,Object>();
        _arguments.put(name, value);
    }

    @XMLProperty
    public String getExecutor() {
        return _executor;
    }

    public void setExecutor(String s) {
        _executor = s;
    }

    public void setExecutor(Class cls) {

        _executor = ((cls != null) ? cls.getName() : null);
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Signature getSignature() {
        return _signature;
    }

    public void setSignature(Signature s) {
        _signature = s;
    }

    @XMLProperty
    public boolean isTemplate() {
        return _template;
    }

    public void setTemplate(boolean b) {
        _template = b;
    }

    @XMLProperty
    public boolean isHidden() {
        return _hidden;
    }

    public void setHidden(boolean b) {
        _hidden = b;
    }

    @XMLProperty
    public String getFormPath() {
        return _formPath;
    }

    public void setFormPath(String path) {
        _formPath = path;
    }

    @XMLProperty
    public void setResultExpiration(int i) {
        _resultExpiration = i;
    }

    public int getResultExpiration() {
        return _resultExpiration;
    }

    @XMLProperty
    public ProgressMode getProgressMode() {
        return _progressMode;
    }

    public void setProgressMode(ProgressMode mode) {
        _progressMode = mode;
    }

    @XMLProperty
    public int getProgressInterval() {
        return _progressInterval;
    }

    public void setProgressInterval(int interval) {
        _progressInterval = interval;
    }

    @XMLProperty(xmlname="RequiredRights", mode=SerializationMode.REFERENCE_LIST)
    public List<SPRight> getRights() {
        return _rights;
    }

    public void setRights(List<SPRight> rights) {
        _rights = rights;
    }

    public void add(SPRight right) {
        if (right != null) {
            if (_rights == null)
                _rights = new ArrayList<SPRight>();
            _rights.add(right);
        }
    }

    public void remove(SPRight right) {
        if (right != null && _rights != null)
            _rights.remove(right);
    }

    public boolean hasRight(SPRight right) {
        return (_rights != null && _rights.contains(right));
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Determine the name of the executor class, traversing the
     * parent hierarchy if necessary.
     */
    public String getEffectiveExecutor() {

        String executor = _executor;
        if (executor == null && _parent != null)
            executor = _parent.getEffectiveExecutor();
        return executor;
    }

    /**
     * Return a flattened collection of attributes.
     * This is a transient object, modifying it will not persist,
     * though modifying the objects it contains might (for example, a shallow copy).
     */
    public Attributes<String,Object> getEffectiveArguments() {

        Attributes<String,Object> flat = new Attributes<String,Object>();
        getEffectiveArguments(flat);
        return flat;
    }

    public void getEffectiveArguments(Attributes<String,Object> args) {

        if (_parent != null)
            _parent.getEffectiveArguments(args);

        if (_arguments != null)
            args.putAll(_arguments);
    }

    /**
     * Get the effective signature.
     * 
     * @ignore
     * Note that Hibernate will usually give us an
     * empty Signature even if one was not defined in XML,
     * probably due to this being a component rather than a reference?
     * Because of this we have to check to see if the non-null
     * Signature is empty too.
     */
    public Signature getEffectiveSignature() {

        Signature sig = _signature;

        // Don't return an empty signature so the task result
        // generator will fall back to the raw map rather than assuming
        // the signature suppresses it.
        if (sig != null && sig.isEmpty())
            sig = null;

        if (sig == null && _parent != null)
            sig = _parent.getEffectiveSignature();

        return sig;
    }

    /**
     * Get the name of the effective definition. This will
     * traverse to the parent definition. This is used to
     * show the name of the parent definition the derivative
     * any definitions are based.
     */
    public String getEffectiveDefinitionName() {

        String defName = _name;
        if ( _parent != null)
            defName = _parent.getName();
        return defName;
    }
    
    /**
     * Get the name of the effective template. This will
     * traverse to the highest parent definition that is a template.
     * Used to figure out what type of report this is.
     */
    public String getRootDefinitionName() {

        String defName = _name;
        if(_parent!=null) {
	        TaskItemDefinition daddy = _parent;
	        while(daddy.getParent()!=null) {
	        	daddy = daddy.getParent();
	        }
	        defName = daddy.getName();
        }
        return defName;
    }
    
    /**
     * Get the name of the effective template. This will
     * traverse to the highest parent definition that is a template.
     * Used to figure out what type of report this is.
     */
    public String getEffectiveFormPath() {

        String formPath = getFormPath();
        if(formPath==null && _parent!=null) {
        	formPath = _parent.getEffectiveFormPath();
        }
        return formPath;
    }

    /**
     * Returns the task definition's task type. If the task type is null, the parent's type
     * is retrieved.
     *
     * @return Task type for the current task definition, or the definitions parent if null.
     */
    public Type getEffectiveType() {
        Type type = _type;
        if(_type == null && _parent != null)
        {
            type = _parent.getEffectiveType();
        }
        return type;
    }

    /**
     * Gets a Message object describing the task subtype for
     * this instance. If the instance does not have a subtype the parent's
     * subtype is used. If the parent does not have a subtype, the instances's
     * effective type is used.
     *
     * The key of the message can be a message key, or plain text if an actual
     * subtype name is used.
     *
     * @return Message containing the subType for this task definition.
     */
    public Message getEffectiveSubType() {
    	Message subTypeValue = null;
    	if(_subType!=null) {
    		subTypeValue = new Message(_subType);
    	} else 	if( _parent != null)
    		subTypeValue = _parent.getEffectiveSubType();
    	else if (getEffectiveType() != null)
            subTypeValue = new Message(getEffectiveType().getMessageKey());
        
    	return subTypeValue;
    }

    public ProgressMode getEffectiveProgressMode() {
        ProgressMode mode = getProgressMode();
        if (mode == null && _parent != null) {
            mode = _parent.getEffectiveProgressMode();
        }
        return mode;
    }
    
    public List<SPRight> getEffectiveRights() {
        List<SPRight> rights = getRights();
        if ((rights == null || rights.isEmpty()) && _parent != null)
            rights = _parent.getEffectiveRights();
        return rights;
    }
    
    public int  getEffectiveProgressInterval() {
        int interval = getProgressInterval();

        //TODO: How do we know if interval is set vs defaulted to 0, since it's a primitive -rap
        if (interval == 0 && _parent != null) {
            interval = _parent.getEffectiveProgressInterval();
        }
        
        return interval;
    }

    public int getEffectiveStateUpdateInterval() {

        int interval = DEFAULT_LOSS_LIMIT;

        Attributes atts = getEffectiveArguments();
        if (atts.containsKey(ARG_LOSS_LIMIT)) {
            interval = atts.getInt(ARG_LOSS_LIMIT);
        }

        return interval;
    }

    public boolean getReportsProgress() {
        boolean reports = false;
        ProgressMode mode = getEffectiveProgressMode();
        if ( mode != null ) {
            if ( ( mode.compareTo(ProgressMode.String) == 0 ) ||
                 ( mode.compareTo(ProgressMode.Percentage) ==0 ) ) {
                reports = true;
            }
        }
        return reports;
    }


    @XMLProperty
	public String getSubType() {
		return _subType;
	}

	public void setSubType(String type) {
		_subType = type;
	}

    public String getString(String name) {
        return (_arguments != null) ? _arguments.getString(name) : null;
    }

    public int getInt(String name) {
        return (_arguments != null) ? _arguments.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_arguments != null) ? _arguments.getBoolean(name) : false;
    }

    public boolean containsKey(String name) {
        return (_arguments != null) ? _arguments.containsKey(name) : false;
    }
    

}
