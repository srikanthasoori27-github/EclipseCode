package sailpoint.api;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class PolicyTreeNodeSummarizer
{
	PolicyTreeNode _node;
	Locale _locale;
	TimeZone _timeZone;

	/**
	 * Constructs a new summarizer for the specified PolicyTreeNode.
	 * @param node The PolicyTreeNode to summarize.
	 */
	public PolicyTreeNodeSummarizer(PolicyTreeNode node, Locale locale, TimeZone timeZone)
	{
		required(node);
		required(locale);
		required(timeZone);

		_node = node;
		_locale = locale;
		_timeZone = timeZone;
	}

	/**
	 * Gets the text summary of this PolicyTreeNode.
	 * @return The summary.
	 * @throws GeneralException
	 */
	public String getSummary()
        throws GeneralException
    {
        Map<String,Object> valuesMap = getValueMap();
        String key = computeKey();
        String message= getMessage(key, valuesMap);
        return message;
    }

    private Map<String,Object> getValueMap() {
        Map<String,Object> vals = new HashMap<String,Object>();

        String entityValue = _node.getDisplayValue();
        if (Util.isEmpty(entityValue)) {
            entityValue = _node.getValue();
        }
        vals.put("value", entityValue);
        vals.put("name", _node.getName());
        vals.put("src", _node.getApplication());
        return vals;
    }

    private String computeKey() {
	    StringBuilder sb = new StringBuilder();
	    sb.append(_node.isEffective() ? "eff" : "direct" );
	    sb.append(_node.isPermission() ? "_perm" : "_ent" );
	    if (!Util.isEmpty(_node.getName())) {
	        sb.append("_for");
        }
        if ("TargetSource".equals(_node.getSourceType())) {
	        sb.append("_targetsrc");
        }
        else {
	        sb.append("_app");
        }
	    return sb.toString();
    }

    private String getMessage(String key, Map<String,Object> values)
    {
        List<Object> args = new ArrayList<>();
        String[] argMapping = keyToArgMapping.get(key);

        if (argMapping != null) {
            for (String valueKey : argMapping) {
                args.add(values.get(valueKey));
            }
        }

        Object[] argArray = (Object[]) args.toArray(new Object[0]);
    	Message msg = new Message(key, argArray);
        return msg.getLocalizedMessage(_locale, _timeZone);
    }
    
    private void required(Object o)
    {
    	if (o == null) {
    		throw new IllegalArgumentException("Value is required");
    	}
    }

    // While the granular levels of message catalog keys seems
    // like overkill, it isn't.  I pinky swear. --ky

    private static Map<String,String[]> keyToArgMapping;
    static {
	    keyToArgMapping = new HashMap<>();

	    // the values in the String[] map corrresponding to:
        //   "value" - the value of the entitlement or permission which needs
        //             to be revoked
        //   "name"  - the name of the entitlement or permission which needs
        //             to be revoked
        //   "src"   - the name of the application or tartet source

        String key = null;
        String[] argMapping = null;

        // Effective

        key = "eff_ent_for_app";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "eff_ent_for_targetsrc";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "eff_ent_app";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);

        key = "eff_ent_targetsrc";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);

        key = "eff_perm_for_app";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "eff_perm_for_targetsrc";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "eff_perm_app";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);

        key = "eff_perm_targetsrc";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);

        // Direct

        key = "direct_ent_for_app";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "direct_ent_for_targetsrc";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "direct_ent_app";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);

        key = "direct_ent_targetsrc";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);

        key = "direct_perm_for_app";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "direct_perm_for_targetsrc";
        argMapping = new String[]{"value","name","src"};
        keyToArgMapping.put(key, argMapping);

        key = "direct_perm_app";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);

        key = "direct_perm_targetsrc";
        argMapping = new String[]{"value","src"};
        keyToArgMapping.put(key, argMapping);
    }
}
