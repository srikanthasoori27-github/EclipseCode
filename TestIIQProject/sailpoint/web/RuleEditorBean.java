/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONStringer;

import sailpoint.integration.XmlUtil;
import sailpoint.object.Argument;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.RuleRegistry;
import sailpoint.object.Signature;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;

/**
 * The RuleEditorBean is used to, well, edit rules...  It's a bit different
 * from most of our other edit beans in that it tracks the DTO outside of the
 * usual architecture.  The mechanism feeding into this bean doesn't have
 * access to the Rule's id, just its name, so the first thing it has to do is
 * look up the Rule by name (or create a new empty one) and store it on the
 * session.  Other than that, the rest of the mechanics are pretty standard.
 *
 * @author derry.cannon
 */
public class RuleEditorBean extends BaseEditBean<Rule>
    {
    private static final String PARAM_RULE_NAME = "ruleName";

    private static Log log = LogFactory.getLog(RuleEditorBean.class);

    private static final String RULE_EDIT = "ruleEdit";

    private String ruleName;

    private String ruleType;

    private Rule template;

    private static RuleRegistry registry = null;


    /**
     * Default constructor, which loads up the rule registry
     * containing the rule templates.
     *
     * @throws GeneralException
     */
    public RuleEditorBean() throws GeneralException
        {
        if (registry == null)
            registry = RuleRegistry.getInstance(getContext());
        }


    // data source methods
    /**
     * Builds a JSON representation of the current rule.
     *
     * @return A JSON representation of the current rule.
     *
     * @throws JSONException
     * @throws GeneralException
     */
    public String getInitJsonData() throws JSONException, GeneralException
        {
        return buildRuleJsonData(getObject().getId(), getName(), getType(), getReturnType(), getSource(), getDescription());
        }

    @Override
    protected String getRequestParameter(String name) {
        // rule name is escaped.  De-escape it
        if (PARAM_RULE_NAME.equals(name)) {
            String ruleNameEscaped = super.getRequestParameter(name);
            return XmlUtil.unescapeAttribute(ruleNameEscaped);
        } else {
            return super.getRequestParameter(name);
        }
    }

    /**
     * Retrieves the requested rule.
     *
     * @return A JSON representation of the requested rule.
     *
     * @throws JSONException
     * @throws GeneralException
     */
    public String getRuleJsonData() throws JSONException, GeneralException
        {
        String ruleName = getRequestParameter(PARAM_RULE_NAME);
        if (ruleName == null)
            // this indicates a JS problem on the client side
            throw new GeneralException("Configuration error: no rule name specified");

        Rule rule = (Rule)getContext().getObjectByName(Rule.class, ruleName);
        if (rule == null)
            throw new GeneralException("No rule found by name: " + ruleName);

        String name = rule.getName();

        String src = rule.getSource();
        if (src != null)
            src = src.trim();
        else
            src = "";

        String desc = rule.getDescription();
        if (desc != null)
            desc = desc.trim();
        else
            desc = "";

        String type = rule.getType().toString();
        String rtnType = rule.getSignature().getReturnType();

        return buildRuleJsonData(rule.getId(), name, type, rtnType, src, desc);
        }


    /**
     * Retrieves all existing rules of the given type.
     *
     * @return A JSON object containing the sorted names of all existing rules
     *         of the given type.
     *
     * @throws GeneralException
     * @throws JSONException
     */
    public String getExistingRulesJsonData() throws GeneralException, JSONException
        {
        // null is a valid option for both the type and the query here
        String type = getRequestParameter("type");
        String query = getRequestParameter("query");

        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        qo.addOwnerScope(super.getLoggedInUser());
        qo.addOrdering("name", true);
        if (type != null)
            qo.add(Filter.ignoreCase(Filter.eq("type", type)));
        if (query != null)
            qo.add(Filter.ignoreCase(Filter.like("name", query, MatchMode.START)));

        List<Rule> rules = getContext().getObjects(Rule.class, qo);
        if (rules == null)
            rules = new ArrayList<Rule>();

        JSONStringer jsonStr = new JSONStringer();
        jsonStr.object();

        jsonStr.key("totalCount");
        jsonStr.value(rules.size());

        jsonStr.key("rules");
        jsonStr.array();

        for (Rule rule : rules)
            {
            jsonStr.object();

            jsonStr.key("name");
            jsonStr.value(XmlUtil.escapeAttribute(rule.getName()));

            jsonStr.endObject();
            }

        jsonStr.endArray();

        jsonStr.endObject();

        return jsonStr.toString();
        }


    /**
     * Returns either the arguments or return types for the current rule,
     * depending on which was requested.
     *
     * @return A JSON object containing the requested args/rtns
     *
     * @throws GeneralException
     * @throws JSONException
     */
    public String getArgsJsonData() throws GeneralException, JSONException
        {
        String query = getRequestParameter("data");
        if (query == null)
            // this indicates a JS problem on the client side
            throw new GeneralException("No data type specified");

        List<Argument> data = null;
        if (query.equals("args"))
            data = getObject().getSignature().getArguments();
        else if (query.equals("rtns"))
            data = getObject().getSignature().getReturns();
        else
            throw new GeneralException("Unsupported data type: " + query);

        // it's very possible not to have any args or rtns
        if (data == null)
            data = new ArrayList<Argument>();

        JSONStringer jsonStr = new JSONStringer();
        jsonStr.object();

        jsonStr.key("totalCount");
        jsonStr.value(data.size());

        jsonStr.key("args");
        jsonStr.array();

        for (Argument arg : data)
            {
            jsonStr.object();

            jsonStr.key("name");
            jsonStr.value(arg.getName());

            jsonStr.key("type");
            jsonStr.value((arg.getType() != null) ? arg.getType() : "");

            String desc = arg.getDescription();
            desc = (desc != null) ? desc.trim() : "";

            jsonStr.key("description");
            jsonStr.value(desc);

            jsonStr.endObject();
            }

        jsonStr.endArray();

        jsonStr.endObject();

        return jsonStr.toString();
        }


    /**
     * Validates the rule before saving.  Currently, it just checks
     * for an existing rule by the same name as the one given. If a
     * rule by that name is found, and this is a save-as, validation
     * fails.  If it's NOT a save-as, compare its id to that of the rule
     * returned by getObject().  If the ids match, we're simply saving the
     * rule we're editing.  If they don't match, we've trying to save a
     * new rule using the same name as an existing rule.
     *
     * @return True if no rule has the given name (other than the rule
     *         currently being edited); false otherwise.
     *
     * @throws GeneralException
     * @throws JSONException
     */
    public String getValidation() throws GeneralException, JSONException
        {
        String ruleName = getRequestParameter(PARAM_RULE_NAME);
        boolean isSaveAs = Boolean.valueOf(getRequestParameter("isSaveAs"));

        if ((ruleName == null) || (ruleName.equals("")))
            // this indicates a JS problem on the client side
            throw new GeneralException("Configuration error: no rule name specified");

        boolean isValid = true;
        Rule rule = (Rule)getContext().getObjectByName(Rule.class, ruleName);

        // trying to save an existing rule with the name of another existing rule
        if ((rule != null) && (isSaveAs))
            isValid = false;

        // trying to save a new rule with the name of an existing rule
        if ((rule != null) && (!rule.getId().equals(getObject().getId())))
            isValid = false;

        JSONStringer jsonStr = new JSONStringer();
        jsonStr.object();

        jsonStr.key("valid");
        jsonStr.value(isValid);

        jsonStr.endObject();

        return jsonStr.toString();
        }


    /**
     * Utility method that takes the give rule information and converts to JSON.
     *
     * @param src     The source code of the given rule
     * @param type    The Rule.Type of the given rule
     * @param rtnType The return type of the given rule
     * @param desc    The description of the given rule
     *
     * @return A JSON representation of the given data.
     *
     * @throws JSONException
     * @throws GeneralException
     */
    private String buildRuleJsonData(String id, String name, String type, String rtnType, String src, String desc)
        throws JSONException, GeneralException
        {
        JSONStringer jsonStr = new JSONStringer();
        jsonStr.object();

        jsonStr.key("id");
        jsonStr.value(id);

        jsonStr.key("name");
        jsonStr.value(WebUtil.safeHTML(name));

        jsonStr.key("type");
        jsonStr.value(type);

        jsonStr.key("returnType");
        jsonStr.value(rtnType);

        jsonStr.key("source");
        jsonStr.value(src);

        jsonStr.key("description");
        jsonStr.value(desc);

        jsonStr.endObject();

        return jsonStr.toString();
        }


    // action methods
    /**
     * Looks up a Rule by the given name and stores it on the session
     * for later use.  If the ruleName is null or empty, then the user
     * is creating a new rule.
     *
     * @return null
     *
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String getLoadup()
        {
        try
            {
            ruleName = getRequestParameter(PARAM_RULE_NAME);
            ruleType = getRequestParameter("ruleType");

            if (ruleType == null)
                // this indicates a JS problem on the client side
                throw new GeneralException("No type specified");
            else
                template = registry.getTemplate(ruleType);

            if (template == null)
                // this indicates a config problem with the rule registry,
                // or possibly incorrect data from the page that made the call
                throw new GeneralException("No template found for type: " + ruleType);

            Rule rule = null;
            if ((ruleName == null) || (ruleName.equals("")))
                {
                // creating a new rule
                rule = new Rule();
                rule.setType(template.getType());
                rule.setDescription(template.getDescription());
                }
            else
                {
                // editing an existing rule
                rule = getContext().getObjectByName(Rule.class, ruleName);
                if (rule == null)
                    throw new GeneralException("No rule found: " + ruleName);

                // this happens when someone either leaves the type off of a 
                // rule imported via xml.  Set the type on the existing rule
                // to the type passed to the editor by the UI.
                if ((rule.getType() == null) || (!ruleType.equals(rule.getType().toString())))
                    {
                    log.warn("Rule found doesn't match the given type: " +
                              rule.getName() + " [" + ruleType + "]");
                    rule.setType(Rule.Type.valueOf(ruleType));
                    
                    // go ahead and save the rule so the type update persists,
                    // even if the user decides not to make any changes to the
                    // rule in the editor
                    getContext().saveObject(rule);
                    getContext().commitTransaction();
                    }
                }

            // make sure the rule sig is current with what's in the template,
            // regardless of whether or not this is a new rule
            rule.setSignature(template.getSignature());

            // set the bean's object, and away we go
            _object = rule;
            _object.load();
            _objectId = _object.getId();

            // store the Rule on the session so we can make multiple
            // ajax requests to the same object
            getSessionScope().put(RULE_EDIT, _object);
            }
        catch (Exception e)
            {
            log.error(e.getMessage(), e);
            }

        return null;
        }


    /**
     * Look up an existing rule by name and use its source and description
     * as templates for the current rule.
     *
     * @return null
     *
     * @throws GeneralException
     */
    public String copyRule() throws GeneralException
        {
        Rule existing = getContext().getObjectByName(Rule.class, ruleName);
        if (existing == null)
            throw new GeneralException("Can't find rule by name: " + ruleName);

        setSource(existing.getSource());
        setDescription(existing.getDescription());

        return null;
        }


    /**
     * Save the current rule to the db.
     *
     * @return null
     *
     * @throws GeneralException
     */
    public String saveRule(ActionEvent e)
        {
        super.saveAction();

        return null;
        }


    /**
     * Create a new rule with the given name, using the current rule's data.
     * The current rule is then replaced in the bean and on the session by
     * the new rule.
     *
     * @return null
     *
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String saveRuleAs(ActionEvent e) throws GeneralException
        {
        Rule newRule = new Rule();
        newRule.setName(getName());
        newRule.setType(Rule.Type.valueOf(getType()));
        newRule.setSource(getSource());
        newRule.setDescription(getDescription());
        newRule.setSignature(getObject().getSignature());

        // set the bean's object, and away we go
        _object = newRule;
        _object.load();
        _objectId = _object.getId();

        // store the Rule on the session so we can access it later
        getSessionScope().put(RULE_EDIT, _object);

        return saveRule(e);
        }


    // getters and setters
    public String getRuleName()
        {
        return this.ruleName;
        }

    public void setRuleName(String rName)
        {
        this.ruleName = rName;
        }

    public String getRuleType()
        {
        return this.ruleType;
        }

    public void setRuleType(String rType)
        {
        this.ruleType = rType;
        }

    public String getName()
        {
        return getObject().getName();
        }

    public void setName(String name)
        {
        getObject().setName(name);
        }

    public String getType() throws GeneralException
        {
        if (getObject().getType() != null)
            return getObject().getType().toString();
        else
            // rules don't HAVE to be typed, although any
            // rule coming through here should be
            return "";
        }

    public void setType(String type) throws GeneralException
        {
        getObject().setType(Rule.Type.valueOf(type));
        }

    public String getSource() throws GeneralException
        {
        if (getObject().getSource() != null)
            return getObject().getSource().trim();
        else
            return "";
        }

    public void setSource(String src) throws GeneralException
        {
        getObject().setSource(src);
        }

    public String getReturnType() throws GeneralException
        {
        if (getObject().getSignature().getReturnType() != null)
            return getObject().getSignature().getReturnType();
        else
            return "";
        }

    public void setReturnType(String rtn) throws GeneralException
        {
        getObject().getSignature().setReturnType(rtn);
        }

    public String getDescription()
        {
        // eliminate any extra spaces or tabs introduced by
        // the XML representation
        if (getObject().getDescription() != null)
            {
            String desc = getObject().getDescription().trim();

            // strip any white space following a newline
            desc = desc.replaceAll("\n[ \t]++", "\n");

            // replace any single newlines with a space
            desc = desc.replaceAll("([\\w\\.,])\n(\\w)", "$1 $2");

            return desc;
            }
        else
            return "";
        }

    public void setDescription(String desc)
        {
        getObject().setDescription(desc);
        }


    // overrides
    // the super.initObjectId() will end up pulling the id of whatever
    // other sailpoint object is currently being edited (e.g., an app id)
    protected void initObjectId() {}


    /**
     * Override the getObject() from BaseEditBean so that we can pull
     * the rule off of the session.
     */
    public Rule getObject()
        {
        if (_object == null)
            {
            _object = (Rule)getSessionScope().get(RULE_EDIT);

            if (_object == null)
                {
                _object = createObject();
                _object.load();
                }
            }

        return _object;
        }


    /**
     * Create an empty Rule, complete with empty Signature
     */
    public Rule createObject()
        {
        Rule rule = new Rule();
        Signature sig = new Signature();
        rule.setSignature(sig);

        return rule;
        }


    // inherited abstract methods
    @Override
    protected Class<Rule> getScope()
        {
        return Rule.class;
        }

    @Override
    public boolean isStoredOnSession()
        {
        return true;
        }
    }
