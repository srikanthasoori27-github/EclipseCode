/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class AccountSelectorRules extends AbstractXmlObject {
    /**
     * Optional rule for selecting target account. 
     * Only applicable if there are profiles.
     */
    Rule _bundleLevelAccountSelectorRule;

    List<ApplicationAccountSelectorRule> _applicationAccountSelectorRules;
    
    public AccountSelectorRules()
    {
    }

    /**
     * Optional rule for selecting target account for the bundle.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="AccountSelectorRuleForBundle")
    public Rule getBundleLevelAccountSelectorRule() {
        return _bundleLevelAccountSelectorRule;
    }

    public void setBundleLevelAccountSelectorRule(Rule r) {
        _bundleLevelAccountSelectorRule = r;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<ApplicationAccountSelectorRule> getApplicationAccountSelectorRules() {
        return _applicationAccountSelectorRules;
    }

    public void setApplicationAccountSelectorRules(List<ApplicationAccountSelectorRule> _applicationAccountSelectorRules) {
        this._applicationAccountSelectorRules = _applicationAccountSelectorRules;
    }

}
