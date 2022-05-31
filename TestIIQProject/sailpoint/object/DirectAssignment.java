/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A representation of the configuration for correlation. This
 * includes both Account Correlation and Manager correlation.
 *
 * An alternate easier way to configure correlation when a 
 * Rule is too complex.
 */
@XMLClass
public class DirectAssignment extends AbstractXmlObject {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    /**
     *  The identity that the will be assigned an account if
     *  the conditions are met.
     */
    Identity _identity;
    
    List<Filter> _filters;

    public DirectAssignment() {
        _filters = new ArrayList<Filter>();
    }

    public DirectAssignment(Identity identity, List<Filter> filters) {
        _identity = identity;
        _filters = filters;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="CorrelatedIdentity")
    public Identity getIdentity() {
        return _identity;
    }

    public void setIdentity(Identity identity) {
        _identity = identity;
    }

    public void setFilters(List<Filter> filters) {
        _filters = filters;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT,xmlname="Conditions")
    public List<Filter> getFilters() {
        return _filters;
    }
    
    public void add(Filter filter) {
        if ( _filters == null )
            _filters = new ArrayList<Filter>();
        
        _filters.add(filter);        
    }
}
