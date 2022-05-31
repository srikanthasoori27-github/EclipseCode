/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * @author peter.holcomb
 * 
 */
package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;


/**
 * The model for a constraint within an activity policy.
 */
public class ActivityConstraint extends BaseConstraint{
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // The SailPointObject._disabled field is used by this subclass to 
    // indicate that the constraint has been disabled.  Normally constraints
    // will be enabled, you might want to disable one if it isn't
    // behaving as expected but you don't want to throw away the entire
    // definition.
    // 
    
    /**
     * Identity Filters - List of filters to apply to an identity when
     * judging who to target when checking whether a violation occurred
     */
    List<Filter> _identityFilters;
    
    /**
     * Activity Filters - List of filters to apply to an activity when
     * judging what activity to target when checking whether a violation
     * occurred
     */
    List<Filter> _activityFilters;
    
    /**
     * Time Periods - a collection of time periods to check the activity 
     * against.
     */
    List<TimePeriod> _timePeriods;

    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ActivityConstraint() {
        // TODO Auto-generated constructor stub
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * List of filters to apply to an activity when
     * judging what activity to target when checking whether a violation
     * occurred.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Filter> getActivityFilters() {
        return _activityFilters;
    }

    public void setActivityFilters(List<Filter> filters) {
        _activityFilters = filters;
    }

    /**
     * List of filters to apply to an identity when
     * judging who to target when checking whether a violation occurred
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Filter> getIdentityFilters() {
        return _identityFilters;
    }

    public void setIdentityFilters(List<Filter> filters) {
        _identityFilters = filters;
    }

    /**
     * A collection of time periods to check the activity 
     * against.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<TimePeriod> getTimePeriods() {
        return _timePeriods;
    }

    public void setTimePeriods(List<TimePeriod> periods) {
        _timePeriods = periods;
    }



}
