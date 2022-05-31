/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.lcm;

import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.DefaultColumn;

/**
 * Column evaluator to calculate the summary of a policy violation.
 * 
 * @author derry.cannon@sailpoint.com
 */
public class WorkItemViolationSummaryColumn extends DefaultColumn 
    {
    protected final static String CACHE_VIOLATION = "violation";
    
    protected final static String FIELD_POLICY_NAME = "policyName";
    protected final static String FIELD_POLICY_OWNER = "policyOwner";
    protected final static String FIELD_DESCRIPTION = "description";
    protected final static String FIELD_RULE_NAME = "ruleName";
    protected final static String FIELD_CONSTRAINT_DESCRIPTION = "constraintDescription";
    protected final static String FIELD_LEFT_BUNDLES = "leftBundles";
    protected final static String FIELD_RIGHT_BUNDLES = "rightBundles";


    @Override
    @SuppressWarnings("unchecked")
    public Object getValue(Map<String, Object> row) throws GeneralException
        {       
        List<String> leftBundles = (List<String>)row.get(FIELD_LEFT_BUNDLES);
        List<String> rightBundles = (List<String>)row.get(FIELD_RIGHT_BUNDLES);
        String desc = (String)row.get(FIELD_DESCRIPTION);
        String constDesc = (String)row.get(FIELD_CONSTRAINT_DESCRIPTION);
        
        if ((!Util.isEmpty(leftBundles)) && (!Util.isEmpty(rightBundles)))
            {
            String left = Util.listToCsv(leftBundles);
            String right = Util.listToCsv(rightBundles);
            Message msg = new Message(MessageKeys.POLICY_VIOLATION_SOD_SUMMARY, left, right);
            return msg.getMessage();
            }
        else if ((null == desc) && ((Util.isEmpty(leftBundles)) || (Util.isEmpty(rightBundles))))
            {
            return constDesc;
            }
        else
            {
            return null;
            }
        }
    }
