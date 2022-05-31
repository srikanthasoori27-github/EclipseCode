/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * 
 * The definition of an ApplicationActivity event that comes from
 * underlying appliations and will be stored into the database.
 *
 * These objects will not need most of the properties
 * found on SailPointObject like createdDate, lastModifiedDate,
 * name, owner or description. However, since the SailPointContext
 * is designed exclusivly to serve SailPointObjects we'll still
 * inherit from SailPointObject.
 * 
 * Since there will be a large number of these its good practice to 
 * keep these objects as compact as possible.
 *
 *  TODO:
 *
 * 1)Figure out if there is a way we can store the ordinal of a
 *   enum for the status.  This would avoid having a separate
 *   constant on the Connector API.
 * 2)Think about generating event or 86 it to avoid bad behavior by connector 
 *   implementors, instead of having explicit setter. 
 *
 * @author dan.smith@sailpoint.com
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The definition of an ApplicationActivity event that comes from
 * underlying applications and will be stored into the database.
 */
@XMLClass
public class ApplicationActivity extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static final long serialVersionUID = 1L;

    /**
     * An enumeration of states the job can be in.
     * Used to both return the current state, and request a new state.
     */
    @XMLClass(xmlname="ActivityResult")
    public static enum Result implements MessageKeyHolder {
        Failure,
        Success;

        public String getMessageKey() {
            return "activity_result_" + this.name().toLowerCase();
        }
    }

    @XMLClass(xmlname="ActivityAction")
    public static enum Action implements MessageKeyHolder{
        Accept,
        Authenticate,
        Complete,
        Create,
        Delete,
        Execute,
        Grant,
        Load,
        Lock,
        Login,
        Logout,
        Read,
        Start,
        Stop,
        Update;

        public String getMessageKey() {
            return "activity_action_" + this.name().toLowerCase();
        }
    }

    /**
     * This comes from the native event, and stores the status
     * of this event, either Result.Success or Result.Failure.
     * The value of this attribute defaults to Result.Success.
     */
    Result _result;

    /**
     * This comes directly from the native event (when)  
     * (it should probably be require for connectors to put this in GMT)
     */
    Date _timeStamp; 

    /** 
     * This is the applicationName (where) 
     * (the connector might do this, but the aggregator could too)
     */
    String _sourceApplication;

    /**
     * This is the dataSource that produced the activity.  This is especially
     * important for Applications that have more then one dataSource.
     */
    String _dataSource;

    /**
     * The instance identifier if _sourceApplication is a template.
     */
    String _instance;

    /**
     * This is the native identity (native who) 
     */
    String _user; 

    /**
     * The action that took place that is interesting, these are things
     * such as Read, Write, Execute...  
     */
    Action _action;

    /** 
     * The actual target of the action.  For example, EmployeeDataTable (onWhat)
     * NOTE: connector will probably need to publish these for the ui too.
     */
    String _target; 

    /** 
     * This is any additional information coming from the native event
     */
    String _info; 

    /** 
     * The hibernate Id of the Identity object, populated by the aggregator 
     */
    String _identityId;

    /** 
     * The name of the Identity object, popuated by the aggregator 
     */
    String _identityName;
    
    /**
     * The TimePeriods during which this activity occurred
     */
    List<TimePeriod> _timePeriods;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ApplicationActivity() {
        _result = Result.Success;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Let the PersistenceManager know there is no queryable name.
     */
    @Override
    public boolean hasName() {
        return false;
    }
  
    @XMLProperty
    public Date getTimeStamp() {
        return _timeStamp;
    }

    public void setTimeStamp(Date date) {
        _timeStamp = date;
    }

    @XMLProperty
    public String getSourceApplication() {
        return _sourceApplication;
    }

    public void setSourceApplication(String source) {
        _sourceApplication = source;
    }

    @XMLProperty
    public String getDataSource() {
        return _dataSource;
    }

    public void setDataSource(String dataSource) {
        _dataSource = dataSource;
    }

    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String ins) {
        _instance = ins;
    }

    @XMLProperty
    public String getUser() {
        return _user;
    }

    public void setUser(String nativeIdentifier) {
        _user = nativeIdentifier;
    }

    /**
     * Returns a string version of some of the event details.
     * Format is "result/action on: target"
     *
     */
    public String getEvent() {
        return getResult() + "/" + getAction() + " on: "  + getTarget();
    }

    @XMLProperty
    public String getTarget() {
        return _target;
    }

    public void setTarget(String target) {
        _target = target;
    }

    @XMLProperty
    public String getInfo() {
        return _info;
    }

    public void setInfo(String info) {
        _info = info;
    }

    @XMLProperty
    public String getIdentityId() {
        return _identityId;
    }

    public void setIdentityId(String identityId) {
        _identityId = identityId;
    }

    @XMLProperty
    public String getIdentityName() {
        return _identityName;
    }

    public void setIdentityName(String identityName) {
        _identityName = identityName;
    }

    /**
     * The native status of the event, 
     * either Result.Success or Result.Failure.
     * The default value is Result.Success.
     */
    @XMLProperty
    public Result getResult() {
        return _result;
    }

    public void setResult(Result status) {
        _result = status;
    }

    @XMLProperty
    public Action getAction() {
        return _action;
    }

    public void setAction(Action action) {
        _action = action;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<TimePeriod> getTimePeriods() {
        return _timePeriods;
    }

    public void setTimePeriods(List<TimePeriod> timePeriods) {
        _timePeriods = timePeriods;
    }

    public void addTimePeriod(TimePeriod tp) {
        if (tp != null) {
            if (_timePeriods == null)
                _timePeriods = new ArrayList<TimePeriod>();
            _timePeriods.add(tp);
        }
    }
    
    public void addTimePeriods(Collection<TimePeriod> tps) {
        if (tps != null && !tps.isEmpty()) {
            if (_timePeriods == null)
                _timePeriods = new ArrayList<TimePeriod>();
            _timePeriods.addAll(tps);
        }
    }
    
    // 
    //  Group of methods from SailPointObject that ApplicationActivity
    //  does not have a need to have persisted. So just have these
    //  methods return null and set null.
    //  
    //  Attributes from SailPointObject that are not necessary:
    //  
    //  name
    //  created
    //  modified
    //  description
    //  owner
    // 

    /** 
     * Name is not used in ApplicationActivity objects. Returns the id.
     */
    @XMLProperty
    public String getName() {
        return getId();
    }

    /** 
     * Name is not used in ApplicationActivity objects. Calling this method 
     * regardless of the value passed, sets the value to null.
     */
    public void setName(String name) {
        _name = null;
    }

    /** 
     * Created is not used in ApplicationActivity objects. Returns null.
     */
    public Date getCreated() {
        return null;
    }

    /** 
     * Created is not used in ApplicationActivity objects. Calling this method 
     * regardless of the value passed, sets the value to null.
     */
    public void setCreated(Date created) {
        _created = null;
    }

    /** 
     * Modified is not used in ApplicationActivity objects. Returns null.
     */
    public Date getModified() {
        return null;
    }

    /** 
     * Modified is not used in ApplicationActivity objects. Calling this method 
     * regardless of the value passed, sets the value to null.
     */
    public void setModified(Date modified) {
        super.setModified(null);
    }

    /** 
     * Description is not used in ApplicationActivity objects. Returns null.
     */
    public String getDescription() {
        return null;
    }

    /** 
     * Description is not used in ApplicationActivity objects. Calling this 
     * method regardless of the value passed, sets the value to null.
     */
    public void setDescription(String description) {
        super.setDescription(null);
    }

    /** 
     * Owner is not used in ApplicationActivity objects. Returns null.
     */
    public Identity getOwner() {
        return null;
    }

    /** 
     * Owner is not used in ApplicationActivity objects. Calling this 
     * method regardless of the value passed, sets the value to null.
     */
    public void setOwner(Identity owner ) {
        super.setOwner(null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * Given an Identity object, pull out the objects id and name.
     */
    public void setIdentity(Identity identity) {
        if ( identity != null ) {
            setIdentityId(identity.getId());
            setIdentityName(identity.getName());
        }
    }

    /** 
     * Given an Identity object, pull out the objects id and name.
     */
    public void setDataSourceObject(ActivityDataSource datasource) {
        if ( datasource != null ) {
            setDataSource(datasource.getName());
        }
    }


    public void setApplication(Application app ) {
        setSourceApplication(app.getName());
    }

    public String toString() {
        return getTimeStamp() + " " + getEvent() + " " + getUser() + " "
               + getIdentityName() + " " + getIdentityId();
    }

    /**
     * Override the default display columns for this object type.
     *
     * @return map of display columns
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("timeStamp", "Date");
        cols.put("action", "Action");
        cols.put("identityName", "Identity");
        cols.put("target", "Target");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     *
     * @return format string
     */
    public static String getDisplayFormat() {
        return "%-34s %-24s %-12s %-20s %s\n";
    }
}
