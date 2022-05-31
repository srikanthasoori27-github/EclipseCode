/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Used to record information about assignments in an Identity.
 * 
 * This is similar to AttributeMetadata but different in that
 * it is recording changes to individual values of a single multi-valued
 * attribute.  There might be other things wanted here.
 *
 * Author: Jeff
 *
 * As of 6.0 this is an abstract class and has two subclasses
 * one for RoleAssignments and the other for 
 * AttributeAssignments.
 * 
 * @see #RoleAssignment
 * @see #AttributeAssignment
 */

package sailpoint.object;

import java.util.Date;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Used to record information about assignments and revocations.
 * 
 * A list of these will be stored in the preferences area of
 * the identity cube. In 6.0 this was factored out of RoleAssignment
 * so it could spawn AttributeAssignment.
 * 
 * @see RoleAssignment
 * @see AttributeAssignment
 * 
 */
@XMLClass
public abstract class Assignment extends AbstractXmlObject
{

    //////////////////////////////////////////////////////////////////////
    // 
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * 
     */
    private static final long serialVersionUID = 38594733822950561L;

    /**
     * Reserved name for the _assigner property to indicate that the
     * assignment was created by a background task.
     */
    public static final String ASSIGNER_SYSTEM = "system";

    /**
     * The name of the Identity that performed the assignment.
     * This might also be some abstract names like "system".
     */ 
    String _assigner;

    /**
     * Date the assignment was made.
     */ 
    Date _date;

    /**
     * Optional identifier of the source of the assignment.
     * These should be the string representations of the
     * sailpoint.object.Source enumeration values, if not it is
     * assumed to be a custom source.
     */
    String _source;
    
    /**
     * True if this is a "negative" assignment.  In this case
     * the role cannot be assigned automatically, though it is
     * not allowed to be assigned manually.
     */
    boolean _negative;

    /**
     * The date in which the assignment starts. (sunrise)
     */
    Date _startDate;

    /**
     * The date in which the assignment ends. (sunset) 
     */
    Date _endDate;

    /**
     * Internal non-persistent property used to hold the Request object
     * that is currently implementing the start date.  
     */
    Request _startRequest;

    /**
     * Internal non-persistent property used to hold the Request object
     * that is currently implementing the start date.  
     */
    Request _endRequest;

    /**
     * The unique generated id of the assignment.
     */
    String _assignmentId;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setAssigner(String s) {
        _assigner = s;
    }

    /**
     * The name of the Identity that performed the assignment
     * (or the revocation).
     * This can also be some abstract names like "System".
     */ 
    public String getAssigner() {
        return _assigner;
    }

    /**
     * Date the assignment (or revocation) was made.
     */ 
    @XMLProperty
    public Date getDate() {
        return _date;
    }

    public void setDate(Date d) {
        _date = d;
    }

    @XMLProperty
    public void setSource(String s) {
        _source = s;
    }

    /**
     * Optional identifier of the source of the assignment.
     */
    public String getSource() {
        return _source;
    }

    public void setSource(Source s) {
        _source = s.toString();
    }

    @XMLProperty
    public void setNegative(boolean b) {
        _negative = b;
    }

    /**
     * True if this is a "negative" assignment.  In this case
     * the role cannot be assigned automatically, though it is
     * allowed to be assigned manually.
     */
    public boolean isNegative() {
        return _negative;
    }

    @XMLProperty
    public Date getStartDate() {
        return _startDate;
    }

    public void setStartDate(Date start) {
        _startDate = start;
    }

    @XMLProperty
    public Date getEndDate() {
        return _endDate;
    }

    public void setEndDate(Date end) {
        _endDate = end;
    }

    public boolean hasTimeFrame() {
        return ( ( _endDate != null )||( _startDate != null ) ) ? true : false;
    }

    @XMLProperty
    public void setAssignmentId(String s) {
        _assignmentId = s;
    }

    /**
     * The unique generated if of the assignment.
     */
    public String getAssignmentId() {
        return _assignmentId;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if this is considered a manual rather than a rule-based
     * assignment.  The EntitlementCorrelator has always assumed this
     * was indicated by having a non-null assigner, but using the Source
     * should also be consider.  For example anything other than
     * Source.Rule means manual, but the Source might not always be set
     * reliably.  
     */
    public boolean isManual() {
        return (_assigner != null || 
                (_source != null && Source.fromString(_source) != Source.Rule));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // XML Backward compatibility
    // I changed some of the names, recognize the old ones for awhile.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    @XMLProperty(xmlname="assignmentDate")
    public void setXmlAssignmentDate(Date d) {
        _date = d;
    }

    /**
     * @exclude
     */
    public Date getXmlAssignmentDate() {
        return null;
    }

    /**
     * Non-persistent property holding the Request object that is currently
     * implementing the scheduled start date.  Intended for internal use
     * by the schedule request generator.
     */
    public Request getStartRequest() {
        return _startRequest;
    }

    public void setStartRequest(Request req) {
        _startRequest = req;
    }

    /**
     * Non-persistent property holding the Request object that is currently
     * implementing the scheduled end date.  Intended for internal use
     * by the schedule request generator.
     */
    public Request getEndRequest() {
        return _endRequest;
    }

    public void setEndRequest(Request req) {
        _endRequest = req;
    }

}
