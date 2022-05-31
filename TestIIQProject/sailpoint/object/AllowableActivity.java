/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;

import sailpoint.object.ApplicationActivity.Action;
import sailpoint.object.ApplicationActivity.Result;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class AllowableActivity extends AbstractXmlObject {

    private List<String> _targets;

    private Action _action; 

    private List<Result> _results;

    public AllowableActivity() {
        _action = null;
        _results = null;
        _targets = null;
    }

    @XMLProperty
    public List<String> getTargets() {
        return _targets;
    }

    public void setTargets(List<String> targets) {
        _targets = targets;
    }

    @XMLProperty
    public Action getAction() {
        return _action;
    }

    public void setAction(Action action) {
        _action = action;
    }

    @XMLProperty
    public List<Result> getResults() {
        return _results;
    }

    public void setResults(List<Result> results) {
        _results = results;
    }
}
