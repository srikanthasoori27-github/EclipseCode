/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for an AttributeSource.
 *
 * Author: Jeff
 */

package sailpoint.web.system;

import sailpoint.object.Application;
import sailpoint.object.AttributeSource;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseDTO;

public class AttributeSourceDTO extends BaseDTO
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    String _application;    
    String _name;
    String _rule;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public AttributeSourceDTO(AttributeSource src) {
        
        _name = src.getName();

        Application app = src.getApplication();
        if (app != null)
            _application = app.getId();

        Rule rule = src.getRule();
        if (rule != null)
            _rule = rule.getId();
    }

    public AttributeSourceDTO(AttributeSourceDTO src) {
        
        _name = src.getName();
        _application = src.getApplication();
        _rule = src.getRule();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getApplication() {
        return _application;
    }

    public void setApplication(String s) {
        _application = trim(s);
    }

    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = trim(s);
    }
    public String getRule() {
        return _rule;
    }

    public void setRule(String s) {
        _rule = trim(s);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    public AttributeSource convert() throws GeneralException {

        AttributeSource src = new AttributeSource();
        src.setName(_name);
        //Not sure where this is used -rap
        src.setApplication(resolveById(Application.class, _application));
        src.setRule(resolveById(Rule.class, _rule));

        return src;
    }


}
    
