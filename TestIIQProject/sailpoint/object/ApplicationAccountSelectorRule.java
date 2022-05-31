/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ApplicationAccountSelectorRule extends AbstractXmlObject {
    Application _application;
    Rule _rule;

    public ApplicationAccountSelectorRule()
    {
    }

    public ApplicationAccountSelectorRule(Application app, Rule rule)
    {
        this._application = app;
        this._rule = rule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application _application) {
        this._application = _application;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public Rule getRule() {
        return _rule;
    }

    public void setRule(Rule _rule) {
        this._rule = _rule;
    }
}
