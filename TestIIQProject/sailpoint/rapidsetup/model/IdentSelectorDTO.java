/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.model;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.CompoundFilter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.service.BaseDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

@SuppressWarnings("serial")
@XMLClass
public class IdentSelectorDTO extends BaseDTO {

    private static Log log = LogFactory.getLog(IdentSelectorDTO.class);

    private static final String KEY_TYPE          = "type";
    private static final String KEY_FILTER_SOURCE = "filterSource";
    private static final String KEY_SCRIPT        = "script";
    private static final String KEY_RULE          = "selectedRule";
    private static final String KEY_POPULATION    = "selectedPopulation";

    public enum SelectorType {
        all,
        filter,
        script,
        rule,
        population
    }

    /**
     * the type of matching the UI intended (SelectorType)
     */
    String _type;

    /**
     * filter source for editing.
     */
    String _filterSource;

    /**
     * Script source.
     * Not allowing the language to be changed.
     */
    String _script;

    /**
     * reference info for the rule
     */
    Map<String,Object> _selectedRule;

    /**
     * reference info for the population
     */
    Map<String,Object>  _selectedPopulation;

    /**
     * true if a match expression was manually added to IdentitySelector
     */
    boolean _matchExpressionFound;

    ///////////////////////////
    // Constructors
    ///////////////////////////
    public IdentSelectorDTO() {
    }

    public IdentSelectorDTO(SailPointContext context, Map<String,Object> values) throws GeneralException {
        validateMap(context, values);
        loadFromMap(context, values);
    }

    private void loadFromMap(SailPointContext context, Map<String,Object> values) throws GeneralException {
        _type = (String)values.get(KEY_TYPE);
        _filterSource  = (String)values.get(KEY_FILTER_SOURCE);
        _script  = (String)values.get(KEY_SCRIPT);
        _selectedRule = (Map<String,Object>)values.get(KEY_RULE);
        _selectedPopulation = (Map<String,Object>)values.get(KEY_POPULATION);
    }

    ///////////////////////////
    // Getters/Setters
    ///////////////////////////

    @XMLProperty
    public String getType() {
        return _type;
    }

    public void setType(String type) {
        _type = type;
    }


    @XMLProperty
    public Map<String,Object> getSelectedRule() {
        return _selectedRule;
    }
    public void setSelectedRule(Map<String,Object> selectedRule) {
        _selectedRule = selectedRule;
    }

    @XMLProperty
    public Map<String,Object> getSelectedPopulation() {
        return _selectedPopulation;
    }
    public void setSelectedPopulation(Map<String,Object> selectedPopulation) {
        _selectedPopulation = selectedPopulation;
    }
    /**
     * Return the Filter as a string for editing in a text area.
     */

    @XMLProperty(mode = SerializationMode.PCDATA, xmlname="RawFilter")
    public String getFilterSource() {
        return _filterSource;
    }

    /**
     * Change the filter source string.
     */
    public void setFilterSource(String src) {
        _filterSource = trim(src);
    }

    @XMLProperty
    public boolean isMatchExpressionFound() {
        return _matchExpressionFound;
    }

    public void setMatchExpressionFound(boolean matchExpressionFound) {
        _matchExpressionFound = matchExpressionFound;
    }

    ///////////////////////////////////
    // Utilities
    ///////////////////////////////////

    public static void validateMap(SailPointContext context, Map<String,Object> values) throws GeneralException {
        String typeString = (String)values.get(KEY_TYPE);
        SelectorType stype;

        try {
            stype = SelectorType.valueOf(typeString);
        }
        catch (IllegalArgumentException e) {
            throw new GeneralException("Unsupported value '" + typeString + "' for identity selector type");
        }

        if (SelectorType.filter == stype) {
            String filterSource  = (String)values.get(KEY_FILTER_SOURCE);
            // for now we edit this as source
            if (filterSource != null) {
                CompoundFilter f = new CompoundFilter();
                // sigh something above us isn't catching
                // RuntimeExceptions which are commonly
                // thrown by the XML parser
                try {
                    f.update(context, filterSource);
                }
                catch (RuntimeException e) {
                    Message msg = new Message(Message.Type.Warn,
                            MessageKeys.UI_APP_ONBOARD_JOINER_FILTER_INVALID, e.getMessage());
                    throw new GeneralException(msg.getLocalizedMessage());
                }
            }
        }
        else if (SelectorType.script == stype) {
            String script  = (String)values.get(KEY_SCRIPT);
            if (script != null) {
                Script s = new Script();
                s.setSource(script);
            }
        }
        else if (SelectorType.rule == stype) {
            Map<String,Object> selectedRuleMap  = (Map<String,Object>)values.get(KEY_RULE);
            if (selectedRuleMap != null) {
                Rule rule = resolveByMap(context, Rule.class, selectedRuleMap);
                if (rule == null) {
                    Message msg = new Message(Message.Type.Warn,
                            MessageKeys.UI_APP_ONBOARD_JOINER_UNKNOWN_RULE, selectedRuleMap.get("id"));
                    throw new GeneralException(msg.getLocalizedMessage());
                }
            }
        }
        else if (SelectorType.population == stype) {
            Map<String,Object> selectedPopulationMap  = (Map<String,Object>)values.get(KEY_POPULATION);
            if (selectedPopulationMap != null) {
                GroupDefinition population = resolveByMap(context, GroupDefinition.class, selectedPopulationMap);
                if (population == null) {
                    Message msg = new Message(Message.Type.Warn,
                            MessageKeys.UI_APP_ONBOARD_JOINER_UNKNOWN_POPULATION, selectedPopulationMap.get("id"));
                    throw new GeneralException(msg.getLocalizedMessage());
                }
            }
        }

    }

    /**
     * Collapse empty strings to null
     */
    public String trim(String src) {
        if (src != null) {
            src = src.trim();
            if (src.length() == 0)
                src = null;
        }
        return src;
    }

    /////////////////////////////////////
    // Conversions
    /////////////////////////////////////

    /**
     * Recreate an IdentitySelector from the DTO bean.
     *
     * Since we make it look like you can only pick one type
     * at a time, we filter the things that aren't relevant to
     * the type. May want to relax this but the UI
     * will have to change to show quite a bit to make the
     * existence of multiple selection types obvious.
     *
     */
    public IdentitySelector convert(SailPointContext context) throws GeneralException {

        IdentitySelector sel = new IdentitySelector();

        if (SelectorType.filter.name().equals(_type)) {
            // for now we edit this as source
            if (_filterSource != null) {
                CompoundFilter f = new CompoundFilter();
                // sigh something above us isn't catching
                // RuntimeExceptions which are commonly
                // thrown by the XML parser
                try {
                    f.update(context, _filterSource);
                }
                catch (RuntimeException e) {
                    throw new GeneralException(e);
                }
                sel.setFilter(f);
            }
        }
        else if (SelectorType.script.name().equals(_type)) {
            if (_script != null) {
                Script s = new Script();
                s.setSource(_script);
                sel.setScript(s);
            }
        }
        else if (SelectorType.rule.name().equals(_type)) {
            if (_selectedRule != null) {
                Rule rule = resolveByMap(context, Rule.class, _selectedRule);
                if (rule != null)
                    sel.setRule(rule);
            }
        }
        else if (SelectorType.population.name().equals(_type)) {
            if (_selectedPopulation != null) {
                GroupDefinition population = resolveByMap(context, GroupDefinition.class, _selectedPopulation);
                if (population != null)
                    sel.setPopulation(population);
            }
        }

        return sel;
    }

    /**
     * Resurect a SailPointObject from an id or name from the map
     */
    static <T extends SailPointObject> T resolveByMap(SailPointContext context, Class<T>cls, Map<String,Object> refMap) {

        T obj = null;

        if (refMap != null) {
            String id = (String)refMap.get("id");
            if (id != null) {
                try {
                    obj = context.getObjectById(cls, id);

                    if (obj == null) {
                        // deleted out from under us, a problem?
                        // just leave the last scope
                        log.warn("Lost reference: " + cls.getSimpleName() + ":" +
                                id);
                    }
                } catch (Throwable t) {
                    // is this worth propagating?
                    log.error(t.getMessage());
                }
            }
        }
        return obj;
    }

}
