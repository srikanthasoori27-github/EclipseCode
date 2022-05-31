/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A representation of the configuration for account correlation. 
 * This is an alternate to Rule based correlation.
 */
@XMLClass
public class CorrelationConfig extends SailPointObject {

    private static final long serialVersionUID = 1L;

    /**
     * Application account attribute to identity attribute assignments
     */
    List<Filter> _attributeAssignments;

    /**
     *  Direct assignment to an identity object based on 
     *  one or more conditions expressed as LeafFilters.
     */
    List<DirectAssignment> _directAssignments;

    public CorrelationConfig() {
        super();
        _attributeAssignments = null;
        _directAssignments = null;
    }

    /**
     * Application account attribute to identity attribute assignments
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public List<Filter> getAttributeAssignments() {
        return _attributeAssignments;
    }

    public void setAttributeAssignments(List<Filter> attributeAssignments) {
        _attributeAssignments = attributeAssignments;
    }

    public void addAttributeAssignment(Filter filter) {
        if ( _attributeAssignments == null ) {
            _attributeAssignments = new ArrayList<Filter>();
        }
        _attributeAssignments.add(filter);
    }

    /**
     * Direct assignment to an identity object based on 
     * one or more conditions expressed as LeafFilters.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public List<DirectAssignment> getDirectAssignments() {
        return _directAssignments;
    }

    public void setDirectAssignments(List<DirectAssignment> direct) {
        _directAssignments = direct;
    }

    public void addDirect(DirectAssignment direct) {
        if ( _directAssignments == null ) {
            _directAssignments = new ArrayList<DirectAssignment>();
        }
        _directAssignments.add(direct);
    }

    public void load() {
        getAttributeAssignments();
        List<DirectAssignment> directAssignments = getDirectAssignments();
        if (!Util.isEmpty(directAssignments)) {
            for (DirectAssignment assignment : directAssignments) {
                Identity assignee = assignment.getIdentity();
                if (assignee != null) {
                    assignee.load();
                }
            }
        }
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitCorrelationConfig(this);
    }

}
