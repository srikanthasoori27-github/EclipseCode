/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A registry that stores which rule to run at certain "Callout" points in the
 * code.
 * 
 * TODO: Eventually we might consider storing a Signature that is valid for each
 * Callout point.  This can be used to enforce signature constraints for
 * different types of rules.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A registry that stores which rule to run at certain "callout" points
 * in the code.
 *
 * There is normally only one of these objects in the database.
 */
@XMLClass
public class RuleRegistry extends SailPointObject
{
    /**
     * 
     */
    private static final long serialVersionUID = 8404038649224579481L;


    /**
     * Enumeration of all Callout points available in the code.
     */
    @XMLClass(xmlname="RuleCallout")
    public enum Callout
    {
        /**
         * Join point called when pass-through authentication is successful but
         * the account does not correlate to a user, which results in a new user
         * being created. This rule transforms the user.
         */
        // TODO: i18n displayName and description
        AUTO_CREATE_USER_AUTHENTICATION("Auto-create user on authentication",
                                        "A rule that accepts an Identity and an account ResourceObject and returns a transformed Identity to be created.  This is called upon successful pass-through authentication when an Identity does not yet exist."),

        /**
         * Default rule used to correlate an application account to 
         * an existing Identity after pass-through authentication or
         * during aggregation.
         * @ignore
         * Hmm, we may actually want a list of these!!  The Aggregator
         * task accepts a list as an argument.
         */
        IDENTITY_CORRELATION("Identity Correlation",
                             "A rule that accepts an account ResourceObject and returns either an Identity name or an Identity object."),

        /**
         * Default rule run on each identity cube during a refresh.
         * This is the last step in the refresh process.
         */
        IDENTITY_REFRESH("Identity Refresh",
                         "A rule that may modify an Identity object during a bulk refresh.");

        private String displayName;
        private String description;

        private Callout(String displayName, String description)
        {
            this.displayName = displayName;
            this.description = description;
        }

        /**
         * Return the display name or an internationalizable message key of the
         * display name.
         * 
         * @return The display name or an internationalizable message key of the
         *         display name.
         */
        public String getDisplayName()
        {
            return (null != this.displayName) ? this.displayName : this.name();
        }

        /**
         * Return the description or an internationalizable message key of the
         * description.
         * 
         * @return The description or an internationalizable message key of the
         *         description.
         */
        public String getDescription()
        {
            return this.description;
        }
    }

    /**
     * The name of the default rule registry object.
     */
    public static final String DEFAULT_RULE_REG = "Rule Registry";

    private Map<Callout, Rule> registry = new HashMap<Callout, Rule>();

    private List<Rule> templates;

    private Map<Rule.Type, Rule> templateMap;

    private List<Rule.Type> templateTypes;
    
    private Rule defaultArgsTemplate;

    private String DEFAULT_ARGS_TEMPLATE = "Default Arguments Template";

    /**
     * Convenience method to load the default RuleRegistry using the given
     * SailPointContext. If a RuleRegistry does not exist, this returns an
     * empty transient instance.
     * 
     * @param  ctx  The SailPointContext to use to load the RuleRegistry.
     * 
     * @return The default RuleRegistry loaded using the given SailPointContext,
     *         or an empty transient instance if the default RuleRegistry does
     *         not exist.
     */
    public static RuleRegistry getInstance(SailPointContext ctx)
        throws GeneralException
    {
        RuleRegistry reg = ctx.getObjectByName(RuleRegistry.class, DEFAULT_RULE_REG);
        if (null == reg)
            reg = new RuleRegistry();
        
        return reg;
    }

    /**
     * @exclude
     * Should not be called - required for annotation-based XML serialization.
     * @deprecated use other accessors for registry info
     */
    @Deprecated
    @XMLProperty
    public Map<Callout, Rule> getRegistry()
    {
        return registry;
    }

    /**
     * @exclude
     * Should not be called - required for annotation-based XML serialization.
     * @deprecated use other accessors for registry info
     */
    @Deprecated
    public void setRegistry(Map<Callout, Rule> registry)
    {
        this.registry = registry;
    }


    @XMLProperty(mode=SerializationMode.UNQUALIFIED_XML)
    public List<Rule> getTemplates() 
    {
        return templates;
    }
    
    /**
     * @exclude
     * Should not be called - required for annotation-based XML serialization.
     * @deprecated use {@link #addTemplate(Rule)}
     */
    @Deprecated
    public void setTemplates(List<Rule> templates) 
    {
        this.templates = templates;
    }

    
    /**
     * Get the Rule to execute for the given Callout point.
     * 
     * @param  callout  The Callout for which to retrieve the Rule to execute.
     * 
     * @return the Rule to execute for the given Callout point.
     */
    public Rule getRule(Callout callout)
    {
        return this.registry.get(callout);
    }

    /**
     * Set the Rule to execute for the given Callout point.
     * 
     * @param  callout  The Callout for which to set the Rule to execute.
     * @param  rule     The Rule to execute for the given Callout point.
     */
    public void setRule(Callout callout, Rule rule)
        throws GeneralException
    {
        this.registry.put(callout, rule);
    }
    
    public Rule getTemplate(String type) throws GeneralException
    {
        return getTemplate(Rule.Type.valueOf(type), true);
    }
    
    public Rule getTemplate(Rule.Type type) throws GeneralException
    {
        return getTemplate(type, true);
    }
    
    /**
     * Return the template for the given Rule.Type
     * 
     * @param type       Rule.Type of the requested template
     * @param returnFull True if the template should have any default arguments
     *                   added to it; false otherwise
     * 
     * @return The template matching the given type; null if not found.
     * 
     * @throws GeneralException
     */
    public Rule getTemplate(Rule.Type type, boolean returnFull) throws GeneralException
    {
        if (templateMap == null)
            initTemplateMap();
        
        Rule tmp = templateMap.get(type);
        if ((tmp != null) && (returnFull))
            loadDefaultArguments(tmp);
        
        return tmp;
    }

    
    /**
     * Helper method to add a rule as a template.
     * 
     * @param template Rule to add as template
     */
    public void addTemplate(Rule template)
    {
        if (getTemplates() != null)
            getTemplates().add(template);
    }
    
    
    /**
     * Helper method to remove a rule from the list of templates.
     * 
     * @param template Rule to remove from templates
     */
    public void removeTemplate(Rule template)
    {
        if (getTemplates() != null)
            getTemplates().remove(template);
    }
    
    
    public List<Rule.Type> getTypes()
    {
        if (templateTypes == null)
        {
            templateTypes = new ArrayList<Rule.Type>();
            for (Rule template : templates)
            {
                Rule.Type t = template.getType();
                templateTypes.add(t);
            }      
            
            Collections.sort(templateTypes);
        }
        
        return templateTypes;
    }

    private void initTemplateMap() throws GeneralException 
    {
        templateMap = new HashMap<Rule.Type, Rule>();
        if (getTemplates() != null)
        {
            for (Rule template : getTemplates())
            {
                // the default args template has no type, so search by name
                if (template.getName().equals(DEFAULT_ARGS_TEMPLATE))
                    defaultArgsTemplate = template;
                
                Rule.Type t = template.getType();
                templateMap.put(t, template);
            }       
            
            // no default args template?  build an empty rule
            if (defaultArgsTemplate == null)
            {
                defaultArgsTemplate = new Rule();
                defaultArgsTemplate.setSignature(new Signature());
            }
        }
    }    
    
    private void loadDefaultArguments(Rule tmp) 
    {
        List<Argument> args = tmp.getSignature().getArguments();
        if (args == null)
        {
            // no standard arguments?
            args = new ArrayList<Argument>();
            tmp.getSignature().setArguments(args);
        }
        
        // add in the default arguments that all rules receive
        for (Argument tmpArg : defaultArgsTemplate.getSignature().getArguments())
        {
            if (!args.contains(tmpArg))
                args.add(0, tmpArg);
        }
    }
}
