/*
 * (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.modeler;

/**
 * ApplicationAccountSelectorRule data transfer helper
 *
 * @author patrick.jeong
 */
public class ApplicationAccountSelectorRuleDTO
{
    private String applicationName;
    private String ruleName;

    public ApplicationAccountSelectorRuleDTO(String appName, String ruleName)
    {
        this.applicationName = appName;
        this.ruleName = ruleName;
    }

    public String getApplicationName()
    {
        return applicationName;
    }

    public void setApplicationName(String applicationName)
    {
        this.applicationName = applicationName;
    }

    public String getRuleName()
    {
        return ruleName;
    }

    public void setRuleName(String ruleName)
    {
        this.ruleName = ruleName;
    }
}
