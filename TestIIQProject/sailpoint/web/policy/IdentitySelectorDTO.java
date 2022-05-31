/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO for IdentitySelector objects.
 *
 * Author: Jeff
 */

package sailpoint.web.policy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.faces.model.SelectItem;

import sailpoint.api.Explanator;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.CompoundFilter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.EntitlementAttributes;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.IdentitySelector.RoleAttributes;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.object.Script;
import sailpoint.object.TargetSource;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.SelectItemByLabelComparator;
import sailpoint.web.util.WebUtil;

@SuppressWarnings("serial")
public class IdentitySelectorDTO extends BaseDTO {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    public static String SELECTOR_TYPE_MATCH = "match";
    public static String SELECTOR_TYPE_FILTER = "filter";
    public static String SELECTOR_TYPE_SCRIPT = "script";
    public static String SELECTOR_TYPE_RULE = "rule";
    public static String SELECTOR_TYPE_POPULATION = "population";
    public static String SELECTOR_TYPE_NONE = "none";
    public static String SELECTOR_TYPE_ALL = "all";
    
    /**
     * Value for _matchType that resticts the allowed value
     * list in MatchTermDTOs to include only attributes from
     * the application schemas that are marked as entitlements.
     */
    public static String MATCH_TYPE_ENTITLEMENT = "entitlement";

    /**
     * Special application ID used for the IdentityIQ identity cube.
     */
    public static final String IIQ_APPLICATION_ID = "IIQ";
    public static final String IIQ_APPLICATION_KEY = BrandingServiceFactory.getService().getSelectorScopeMessageKey(); 

    /**
     * Internal values to represent and/or of match terms.
     */
    public static final String OP_AND = "and";
    public static final String OP_OR = "or";

    /**
     * maximum depth of matchterms
     * it is only limited by ui, not by backend logic
     */
    private static final int MAX_DEPTH = 3;


    String _id;
    String _type;
    String _summary;

    boolean _allowTypeAll;
    boolean _allowTypeNone;
    
    /**
     * When set, it restricts the attributes included on the
     * allowed value list of each MatchTerm.  There is only one
     * right now, but were leaving it open for expansion.
     * The IdentitySelector model doesn't have this, but since
     * we're buidling the SelectItem lists down here, it has
     * to be passed down from PolicyDTO where it is derived
     * based on the policy type.
     */ 
    String _matchType;

    /**
     * We'll simply the DTO Model and inline the MatchExpression fields.
     */
    MatchExpressionDTO _matchExpression;

    /**
     * Filter.  We currently edit these as source but will
     * need to include a UI for direct Filter editing eventually.
     */
    CompoundFilter _filter;

    /**
     * Temporary filter source for editing.
     */
    String _filterSource;

    /**
     * Script source.
     * Not allowing the language to be changed.
     */
    String _script;

    /**
     * The name of the selected match rule.
     */
    String _rule;

    /**
     * The name of the selected population.
     */
    String _population;

    /**
     * The id of the currently selected application, used
     * when creating new MatchTermDTOs.
     */
    String _application;

    String _targetSource;

    /**
     * Cache of the available applications.
     * UtilBean also has this but we need to insert
     * our spcecial item for the IdentityIQ Identity.
     */
    List<SelectItem> _applications;

    /**
     * Cached list of match operators when selecting and/or from
     * a menu.
     */
    List<SelectItem> _matchOperators;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * No arg constructor for serialization.
     */
    public IdentitySelectorDTO() {
        _id = BaseDTO.uuid();
        _type = SELECTOR_TYPE_MATCH;
    }

    public IdentitySelectorDTO(IdentitySelector src) {
        this(src, null);
    }

    public IdentitySelectorDTO(IdentitySelector src, boolean allowTypeNone) {
        this(src, null, allowTypeNone);
    }
    public IdentitySelectorDTO(IdentitySelector src, boolean allowTypeNone, boolean allowTypeAll) {
        this(src, null, allowTypeNone, allowTypeAll);
    }

    public IdentitySelectorDTO(IdentitySelector src, String matchType) {
        this(src, matchType, false);
    }
    
    public IdentitySelectorDTO(IdentitySelector src, String matchType,
            boolean allowTypeNone) {
        this(src, matchType, allowTypeNone, false);
    }

    /**
     * The IdentitySelector's owning object must still
     * be attached to the Hibernate session so we can
     * lazy load (or it must be fully fetched).
     */
    public IdentitySelectorDTO(IdentitySelector src, String matchType,
                               boolean allowTypeNone, boolean allowTypeAll) {

        _id = BaseDTO.uuid();
        _type = (allowTypeNone) ? SELECTOR_TYPE_NONE : SELECTOR_TYPE_MATCH;
        _matchType = matchType;
        _allowTypeNone = allowTypeNone;
        _allowTypeAll = allowTypeAll;

        if (src != null) {
            MatchExpression exp = src.getMatchExpression();
            if (exp != null) {
                _matchExpression = new MatchExpressionDTO(exp, _matchType);
                
                _type = SELECTOR_TYPE_MATCH;
            }

            // no bean wrapper for this yet
            _filter = src.getFilter();

            // for now require this be edited as source
            if (_filter != null)
                _filterSource = _filter.render();

            // not letting you select a language yet so just
            // manage the source string
            Script script = src.getScript();
            if (script != null)
                _script = script.getSource();
            
            Rule rule = src.getRule();
            if (rule != null) 
                _rule = rule.getName();

            GroupDefinition pop = src.getPopulation();
            if (pop != null)
                _population = pop.getId();

            // The UI only shows one type at a time, though
            // there can structurally be more than one type.
            // Avoid confusion by making it look like selectors
            // have a type, need to decide if the selected type
            // means we take the other types away on commit?
            
            if (_filter != null)
                _type = SELECTOR_TYPE_FILTER;

            // test Script object which may have null source
            else if (script != null)
                _type = SELECTOR_TYPE_SCRIPT;

            else if (_rule != null)
                _type = SELECTOR_TYPE_RULE;

            else if (_population != null)
                _type = SELECTOR_TYPE_POPULATION;

            // NOTE: IdentitySelector has a _summary property but we're
            // not going to show that for editing.  We will always
            // generate a summary string that attempts to describe what
            // the selector does.  
            // _summary = src.getSummary();
            if (_summary == null)
                refreshSummary();
        }
    }

    /**
     * Used when cloning for edit.
     */
    public IdentitySelectorDTO(IdentitySelectorDTO src) {

        _id = src.getId();
        _type = src.getType();
        _summary = src.getSummary();
        _matchType = src.getMatchType();

        _matchExpression = src.getMatchExpression();

        // eventually will need to deal with these
        //_filter = src.cloneCompountFilter();
        _filterSource = src.getFilterSource();

        _script = src.getScriptSource();
        _rule = src.getRule();
        _population = src.getPopulation();

        // _application is a transient field used when 
        // adding new terms, I suppose we could at
        // least start with the last selection though?
        _application = src.getApplication();
    }

    /**
     * Generate a brief summary string for the rule table.
     */
    public void refreshSummary() {

        if (SELECTOR_TYPE_FILTER.equals(_type)) {
            // sigh, this is rather ugly the FilterRenderer representation
            // would look better
            _summary = "Filter: " + ((_filterSource != null) ? _filterSource : "");
        }
        else if (SELECTOR_TYPE_SCRIPT.equals(_type)) {
            _summary = "Script: " + ((_script != null) ? _script : "");
        }
        else if (SELECTOR_TYPE_RULE.equals(_type)) {
            _summary = "Rule";
            if (_rule != null) {
                Rule rule = resolveByName(Rule.class, _rule);
                if (rule != null)
                    _summary = "Rule: " + rule.getName();
            }
        }
        else if (SELECTOR_TYPE_POPULATION.equals(_type)) {
            _summary = "Population";
            if (_population != null) {
                GroupDefinition pop = resolveById(GroupDefinition.class, _population);
                if (pop != null)
                    _summary = "Population: " + pop.getName();
            }
        }
        else if (SELECTOR_TYPE_NONE.equals(_type)) {
            _summary = "";
        }
        else {
            _summary = renderMatch();
        }

        _summary = trim(_summary, 80);
    }
    
    /**
     * This is approxomately like what IdentitySelector.MatchExpression does.
     */ 
    public String renderMatch() {

        if (_matchExpression != null) {
            return _matchExpression.render();
        }

        return "";
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getId() {
        return _id;
    }

    public String getMatchType() {
        return _matchType;
    }

    public boolean isAllowTypeNone() {
        return _allowTypeNone;
    }

    public void setAllowTypeNone(boolean allow) {
        _allowTypeNone = allow;
    }
    
    public boolean isAllowTypeAll() {
        return _allowTypeAll;
    }

    public void setAllowTypeAll(boolean allow) {
        _allowTypeAll = allow;
    }

    public String getApplication() {
        return _application;
    }

    public void setApplication(String name) {
        _application = trim(name);
    }

    public String getTargetSource() {
        return _targetSource;
    }

    public void setTargetSource(String name) {
        _targetSource = trim(name);
    }

    /**
     * This is a derived property that for the livegrid,
     * you do not edit this.
     */
    public String getSummary() {
        return _summary;
    }

    /**
     * TODO: In theory a selector can have more than
     * one matching operator, but we'll pretend you can
     * only have one for now.
     */
    public String getType() {
        return _type; 
    }

    public void setType(String s) {
        _type = s;
    }
    
    public MatchExpressionDTO getMatchExpression() {
        return _matchExpression;
    }

    public List<MatchTermDTO> getMatchTerms() {
        return _matchExpression.getTerms();
    }
    
    public int getMatchTermCount() {
        return (_matchExpression != null) ? _matchExpression.getTerms().size() : 0;
    }

    public boolean isTermAnd() {
        return _matchExpression.isAnd();
    }

    public void setTermAnd(boolean b) {
        _matchExpression.setAnd(b);
    }

    /**
     * Alternate property for setting and/or with a menu.
     */
    public String getMatchOperator() {
        return (isTermAnd()) ? OP_AND : OP_OR;
        
    }

    public void setMatchOperator(String op) {
        setTermAnd(OP_AND.equals(op));
    }

    public List<SelectItem> getMatchOperators() {
        if (_matchOperators == null) {
            _matchOperators = new ArrayList<SelectItem>();
            _matchOperators.add(new SelectItem(OP_AND, getMessage(MessageKeys.FILTER_AND)));
            _matchOperators.add(new SelectItem(OP_OR, getMessage(MessageKeys.FILTER_OR)));
        }
        return _matchOperators;
    }

    /**
     * Return the Filter as a string for editing in a text area.
     */
    public String getFilterSource() {
        return _filterSource;
    }

    /**
     * Change the filter source string.
     * TODO: Might want to validate it now?
     */
    public void setFilterSource(String src) {

        _filterSource = trim(src);
    }
    
    /**
     * Return the Script as a string for editing in a text area.
     * If we ever wanted to support other languages would need a bean.
     */
    public String getScriptSource() {
        return _script;
    }

    /**
     * Change the script source string.
     * TODO: Might want to validate it now?
     */
    public void setScriptSource(String src) {
        _script = trim(src);
    }
    
    public String getRule() {
        return _rule;
    }

    public void setRule(String s) {
        _rule = trim(s);
    }

    public String getPopulation() {
        return _population;
    }

    public void setPopulation(String s) {
        _population = trim(s);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    public void addMatchAttribute() throws GeneralException {

        addMatchTerm(false, IdentitySelector.MatchTerm.Type.Entitlement.name());
    }

    public void addMatchPermission() throws GeneralException {

        addMatchTerm(false, IdentitySelector.MatchTerm.Type.Permission.name());
    }
    
    public void groupSelectedTerms() {

        if (!checkDepth()) {
            return;
        }
        
        List<MatchTermDTO> selecteds = new ArrayList<MatchTermDTO>();
        for (MatchTermDTO term : _matchExpression.getTerms()) {
            if (term.isSelected()) {
                selecteds.add(term);
            }
        }

        for (MatchTermDTO selected : selecteds) {
            _matchExpression.getTerms().remove(selected);
        }
        
        if (selecteds.size() > 0) {
            MatchTermDTO newGroup = new MatchTermDTO();
            newGroup.setContainer(true);
            newGroup.setChildren(selecteds);
            newGroup.getChildren().get(0).setFirst(true);
            _matchExpression.addTerm(newGroup);
        }

        unselectAll();

        setFirst();
    }
    
    private boolean checkDepth() {
        if (_matchExpression.getDepth() == MAX_DEPTH) {
            //!!! localize
            addMessage(new Message(Type.Error, "Expressions can only be 3 levels deep"));
            return false;
        }
        
        return true;
    }
    
    public void ungroupSelectedTerms() {

        List<MatchTermDTO> toAdd = new ArrayList<MatchTermDTO>();
        List<MatchTermDTO> toRemove = new ArrayList<MatchTermDTO>();
        
        for (MatchTermDTO term : _matchExpression.getTerms()) {
            if (term.isSelected()) {
                if (term.isContainer()) {
                    term.ungroupChildren(toAdd);
                    toRemove.add(term);
                }
            }
        }
        
        for (MatchTermDTO term : toAdd) {
            _matchExpression.addTerm(term);
        }
        
        for (MatchTermDTO term : toRemove) {
            _matchExpression.getTerms().remove(term);
        }
        
        unselectAll();
        
        setFirst();
    }
    
    private void setFirst() {
        for (MatchTermDTO term : _matchExpression.getTerms()) {
            term.setFirst(false);
        }
        
        if (_matchExpression.getTerms().size() > 0) {
            _matchExpression.getTerms().get(0).setFirst(true);
        }
    }
    
    private void unselectAll() {
        for (MatchTermDTO term :  _matchExpression.getTerms()) {
            term.unselectAll();
        }
    }

    /**
     * This only deals with Permission and Entitlement types.
     * IdentityAttribute has Entitlement Type and null Application.
     * 
     * Deprecated when we have new types, IdentityAttribute, RoleAttribute, EntitlementAttribute.
     * 
     * @deprecated use addMatchTerm(boolean checkEffective, String type)
     */
    @Deprecated
    public void addMatchTerm(boolean permission) throws GeneralException {
        String type = permission ? IdentitySelector.MatchTerm.Type.Permission.name() :
                MatchTerm.Type.Entitlement.name();
        addMatchTerm(false, type);
    }

    public void addMatchTerm(boolean checkEffective, String type) throws GeneralException {

        if (_application == null && _targetSource == null) {
            throw new GeneralException(new Message(Type.Error, MessageKeys.ERROR_SELECTOR_NO_SOURCE_SELECTED));
        }

        MatchTermDTO matchTermDTO = null;

        if (_application != null) {
            if (_application.equals(IIQ_APPLICATION_ID)) {
                // ignore the permission flag
                matchTermDTO = new MatchTermDTO((Application)null, type, checkEffective, _matchType);
            } else {
                Application app = getContext().getObjectByName(Application.class, _application);
                if (app == null)
                    //throw new GeneralException(new Message(Messages.POLICY_INVALID_APPLICATION, _application));
                    throw new GeneralException(new Message("policy_invalid_application", _application));
                else
                    app.load();

                matchTermDTO = new MatchTermDTO(app, type, checkEffective, _matchType);
            }
        }
        else {
            TargetSource targetSource = getContext().getObjectByName(TargetSource.class, _targetSource);
            if (targetSource == null)
                throw new GeneralException(new Message("policy_invalid_target_source", _targetSource));
            else
                targetSource.load();

            matchTermDTO = new MatchTermDTO(targetSource, type, checkEffective, _matchType);
        }

        // forces the type, should already be set
        _type = SELECTOR_TYPE_MATCH;
        
        if (_matchExpression == null) {
            _matchExpression = new MatchExpressionDTO(new MatchExpression(), _matchType);
        }
        _matchExpression.addTerm(matchTermDTO);
        
        setFirst();
    }

    public void deleteSelectedTerms() {

        if (_matchExpression != null) {
            List<MatchTermDTO> neu = new ArrayList<MatchTermDTO>();
            for (MatchTermDTO term :  _matchExpression.getTerms()) {
                if (!term.isSelected())
                    neu.add(term);
            }
            _matchExpression.setTerms(neu);
            setFirst();
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Validate the contents of the selector before
     * we leave the editing page.  The only things
     * we care about are the filter and script sources.
     */
    public void validate() throws GeneralException {
        if (SELECTOR_TYPE_FILTER.equals(_type)) {
            if (_filterSource == null || _filterSource.length() == 0) {
                throw new GeneralException(new Message(Message.Type.Warn,
                        MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_NO_FILTER));
            }
            // for now we edit this as source
            if (_filterSource != null) {
                CompoundFilter f = new CompoundFilter();
                // this may throw
                // sigh something above us isn't catching
                // RuntimeExceptions which are commonly
                // thrown by the XML parser
                try {
                    f.update(getContext(), _filterSource);
                }
                catch (RuntimeException e) {
                    throw new GeneralException(e);
                }
            }
        }
        else if (SELECTOR_TYPE_SCRIPT.equals(_type)) {
            if (_script == null || _script.length() == 0) {
                throw new GeneralException(new Message(Message.Type.Warn,
                        MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_NO_SCRIPT));
            }
            if (_script != null) {
                Script s = new Script();
                s.setSource(_script);
                // !! TODO: need to try and evaluate this but
                // the BeanShell exceptions are obscure and hard
                // to format
            }
        }
        else if (SELECTOR_TYPE_MATCH.equals(_type)) {
            // I suppose we could look for things like terms
            // with missing names or values...
            
            if (_matchExpression == null) {
                throw new GeneralException(new Message(Message.Type.Warn,
                        MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_MATCH_TERMS));
            }
            
            List<MatchTermDTO> terms = _matchExpression.getTerms();
            
            if (terms == null || terms.size() == 0) {
                throw new GeneralException(new Message(Message.Type.Warn,
                        MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_MATCH_TERMS));
            }
            
            validateTerms(terms);
        }
        else if (SELECTOR_TYPE_POPULATION.equals(_type)) {
            if (_population == null) {
                throw new GeneralException(new Message(Message.Type.Warn,
                        MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_NO_POPULATION));
            }
        } 
        else if (SELECTOR_TYPE_RULE.equals(_type)) {
            if (_rule == null) {
                throw new GeneralException(new Message(Message.Type.Warn,
                        MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_NO_RULE));
            }
        }
    }
    
    /**
     * validate match terms recursively
     * 
     * @param terms
     */
    private void validateTerms(List<MatchTermDTO> terms) throws GeneralException {
        String name,value;
        for (MatchTermDTO term : terms) {
            // make sure the terms have name and values
            if (!term.isContainer()) {
                name = term.getName();
                value = term.getValue();
                if (name == null || name.length() == 0) {
                    throw new GeneralException(new Message(Message.Type.Warn,
                            MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_MATCH_TERMS));
                }
                if (!term.getIsNull() && (value == null || value.length() == 0)) {
                    throw new GeneralException(new Message(Message.Type.Warn,
                            MessageKeys.ERROR_SELECTOR_INCOMPLETE_CONFIG_MATCH_TERMS));
                }
            }
            else {
                validateTerms(term.getChildren());
            }
        }
    }

    /**
     * Recreate an IdentitySelector from the DTO bean.
     *
     * Since we make it look like you can only pick one type
     * at a time, we filter the things that aren't relevant to 
     * the ending type. May want to relax this but the UI
     * will have to change to show quite a bit to make the
     * existance of multiple selection types obvious.
     *
     */
    public IdentitySelector convert() throws GeneralException {

        IdentitySelector sel = new IdentitySelector();

        if (SELECTOR_TYPE_FILTER.equals(_type)) {
            // for now we edit this as source
            if (_filterSource != null) {
                CompoundFilter f = new CompoundFilter();
                // sigh something above us isn't catching
                // RuntimeExceptions which are commonly
                // thrown by the XML parser
                try {
                    f.update(getContext(), _filterSource);
                }
                catch (RuntimeException e) {
                    throw new GeneralException(e);
                }
                sel.setFilter(f);
            }
        }
        else if (SELECTOR_TYPE_SCRIPT.equals(_type)) {
            if (_script != null) {
                Script s = new Script();
                s.setSource(_script);
                sel.setScript(s);
            }
        }
        else if (SELECTOR_TYPE_RULE.equals(_type)) {
            if (_rule != null) {
                Rule rule = resolveByName(Rule.class, _rule);
                if (rule != null)
                    sel.setRule(rule);
            }
        }
        else if (SELECTOR_TYPE_POPULATION.equals(_type)) {
            if (_population != null) {
                GroupDefinition pop = resolveById(GroupDefinition.class, _population);
                if (pop != null)
                    sel.setPopulation(pop);
            }
        }
        else if (SELECTOR_TYPE_NONE.equals(_type)) {
            sel = null;
        }
        else {
            // Always defaults to MATCH if not specified.
            // Currently not supporting an Application scope
            // at the MatchExpression level, will have an Application
            // in each term.
            if (_matchExpression != null && _matchExpression.getTerms().size() > 0) {
                MatchExpression exp = _matchExpression.convert();
                sel.setMatchExpression(exp);
            }
        }

        return sel;
    }
    
    public static class MatchExpressionDTO extends BaseDTO implements Serializable {
        
        boolean and;
        List<MatchTermDTO> terms = new ArrayList<MatchTermDTO>();

        public MatchExpressionDTO(MatchExpression expression, String matchType) {
            this.and = expression.isAnd();

            for (MatchTerm term : expression.getTerms()) {
                this.terms.add(new MatchTermDTO(term, matchType));
            }
            if (this.terms.size() > 0) {
                this.terms.get(0).setFirst(true);
            }
        }
        
        public MatchExpression convert() throws GeneralException {
            
            MatchExpression expression = new MatchExpression();
            expression.setAnd(this.and);

            for (MatchTermDTO dto : this.terms) {
                MatchTerm term = dto.convert();
                if (term != null) // don't add nulls, it confuses the UI
                    expression.addTerm(term);
            }

            return expression;
        }
        
        public boolean isAnd() {
            return this.and;
        }
        public void setAnd(boolean and) {
            this.and = and;
        }
        
        public List<MatchTermDTO> getTerms() {
            return this.terms;
        }
        public void setTerms(List<MatchTermDTO> val) {
            this.terms = val;
        }
        
        public void addTerm(MatchTermDTO val) {
            this.terms.add(val);
            if (this.terms.size() > 0) {
                this.terms.get(0).setFirst(true);
            }
        }
        
        public int getDepth() {
            int depth = 0;
            for (MatchTermDTO term : this.terms) {
                if (term.getDepth() > depth) {
                    depth = term.getDepth();
                }
            }
            return depth;
        }
        
        public String render() {
            StringBuilder builder = new StringBuilder();
            Iterator<MatchTermDTO> it = this.terms.iterator();
            while (it.hasNext()) {
                builder.append("(" + it.next().render() + ")");
                if (it.hasNext()) {
                    builder.append(" " + (this.and ? "AND" : "OR") + " ");
                }
            }
            return builder.toString();
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // MatchTermDTO
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * DTO for state related to one MatchExpression term.
     * We don't support application scoping on the MatchExpression,
     * applications are set for each term.  By the time we build
     * this bean the application must already have been set, we
     * don't expect the application or permission flag to be modified
     * in the UI, you create them with the desired application and permission
     * and then edit just the name and value.
     */
    public static class MatchTermDTO extends BaseDTO implements Serializable {
        /**
         * Internal values to represent eq/notEq/isNull of match terms.
         */
        public static final String OPERATOR_EQ = "eq";
        public static final String OPERATOR_NOT_EQ = "notEq";
        public static final String OPERATOR_NULL = "isNull";

        boolean _selected;
        boolean _isNull;
        boolean _isNegative;
        String _applicationId;
        String _applicationName;
        String _targetSourceId;
        String _targetSourceName;
        boolean _permission;
        String _type;
        String _name;
        String _value;
        List<SelectItem> _allowedNames;
        boolean _first;
        boolean _container;
        String _operation = OP_OR;
        String _operator = OPERATOR_EQ;
        List<MatchTermDTO> _children = new ArrayList<MatchTermDTO>();
        private String _displayValue;
        private boolean _checkEffective;
        List<ContributingEntitlementDTO> _contributingEntitlements;


        /**
         * Cached list of match operators when selecting and/or from
         * a menu.
         */
        List<SelectItem> _matchTermOperators;

        /**
         * This is used when building the DTO hierarchy
         * for the first time.  The object the IdentitySelector is in must
         * still be attached to the Hibernate session so we can get the
         * Application.
         */
        public MatchTermDTO(MatchTerm src, String matchType) {

            if (src != null) {
                _permission = src.isPermission();
                _name = src.getName();
                _value = src.getValue();
                _checkEffective = src.isCheckEffective();
                
                if (null == _value) {
                    _isNull = true;
                } else {
                    _isNull = false;
                }

                //_type is needed in setApplication()
                if (src.getType() != null) {
                    _type = src.getType().toString();
                }

                if (src.getTargetSource() != null) {
                    setTargetSource(src.getTargetSource(), matchType);
                }
                else {
                    setApplication(src.getApplication(), matchType, src.isCheckEffective());
                }
                
                _container = src.isContainer();
                _operation = src.isAnd() ? OP_AND : OP_OR;
                
                _isNegative = src.isNegative();
                
                if (_isNull) {
                    _operator = OPERATOR_NULL;
                } else if (_isNegative) {
                    _operator = OPERATOR_NOT_EQ;
                } else {
                    _operator = OPERATOR_EQ;
                }
                
                for (MatchTerm child : src.getChildren()) {
                    _children.add(new MatchTermDTO(child, matchType));
                }
                
                if (_children.size() > 0) {
                    _children.get(0).setFirst(true);
                }

                if (src.getContributingEntitlements() != null) {
                    _contributingEntitlements = new ArrayList<ContributingEntitlementDTO>();
                    for (MatchTerm.ContributingEntitlement ce : src.getContributingEntitlements() ) {
                        ContributingEntitlementDTO ceDTO = new ContributingEntitlementDTO(ce);
                        _contributingEntitlements.add(ceDTO);
                    }
                }
            }
        }

        /**
         * Copy the id and name of the scoping application, and calculate allowedNames.
         * @param app the selected Application
         * @param matchType the type of matching to be performed
         * @param checkEffective if true, then all schemas will be examined for allowedNames
         */
        private void setApplication(Application app, String matchType, boolean checkEffective) {
            if (app != null) {
                _applicationId = app.getId();
                _applicationName = app.getName();
                _allowedNames = getAllowedNames(app, matchType, checkEffective);
            }
            else {
                _applicationId = IIQ_APPLICATION_ID;
                _applicationName = getMessage(IIQ_APPLICATION_KEY);
                if (MatchTerm.Type.RoleAttribute.name().equals(_type)) {
                    _allowedNames = getRoleAttributeNames();
                } else if (MatchTerm.Type.EntitlementAttribute.name().equals(_type)) {
                    _allowedNames = getEntitlementAttributeNames();
                } else {
                    //Old IdentityAttribute MatchTerm has Entitlement Type with null Application.
                    _allowedNames = getIdentityAttributeNames();
                }
            }

            if (!Util.isEmpty(_allowedNames)) {
                Comparator<SelectItem> allowedNameComparator = new SelectItemByLabelComparator(getLocale());
                Collections.sort(_allowedNames, allowedNameComparator);

            }

            // bug#6382 JSF doesn't like having an empty select list, it
            // dies in UISelectOne.validateValue, give the selecctor
            // an initial label like we do for other selectors.
            // Note that this has to be done after 
            // SelectItemByLabelComparator sorting
            if (Util.isEmpty(_allowedNames))
                _allowedNames = new ArrayList<SelectItem>();
            _allowedNames.add(0, new SelectItem("", getMessage(MessageKeys.SELECTOR_SELECT_ATTRIBUTE)));
        }

        /**
         * Copy the id and name of the scoping targetSource.
         */
        private void setTargetSource(TargetSource targetSource, String matchType) {
            if (targetSource != null) {
                _targetSourceId = targetSource.getId();
                _targetSourceName = targetSource.getName();
                _applicationId = null;
                _applicationName = null;
            }
            else {
                _targetSourceId = null;
                _targetSourceName = null;
            }
        }

        /**
         * This is used when cloning for editing.
         */
        public MatchTermDTO(MatchTermDTO src) {
            if (src != null) {
                _applicationId = src.getApplicationId();
                _applicationName = src.getApplicationName();
                _targetSourceId = src.getTargetSourceId();
                _targetSourceName = src.getTargetSourceName();
                _permission = src.isPermission();
                _name = src.getName();
                _value = src.getValue();
                _checkEffective = src.isCheckEffective();

                _first = src.isFirst();
                _container = src.isContainer();
                _operation = src.getOperation();
                _operator = src.getOperator();
                _type = src.getType();

                for (MatchTermDTO child : src.getChildren()) {
                    addChild(child);
                }

                // these don't change so just share them
                _allowedNames = src.getAllowedNames();
                // jsl - why are we resorting them?  this screws up
                // if there is a --Select Something-- header, they should
                // already be sorted
                /*
                if (_allowedNames != null) {
                    Comparator<SelectItem> allowedNameComparator = new SelectItemByLabelComparator(getLocale());
                    Collections.sort(_allowedNames, allowedNameComparator);
                }
                */
            }
        }

        /**
         * This is used when creating new terms.
         */
        public MatchTermDTO(Application app, boolean permission, boolean checkEffective,
                            String matchType) {

            _permission = permission;
            _checkEffective = checkEffective;
            _type = permission ? MatchTerm.Type.Permission.name() : MatchTerm.Type.Entitlement.name();

            setApplication(app, matchType, checkEffective);
        }

        /**
         * This is used when creating new terms.
         */
        public MatchTermDTO(Application app, String type, boolean checkEffective,
                            String matchType) {

            _type = type;
            _checkEffective = checkEffective;
            _permission = MatchTerm.Type.Permission.name().equals(_type);

            setApplication(app, matchType, checkEffective);
        }
        
        //need this to create container
        public MatchTermDTO() {
            _applicationId = IIQ_APPLICATION_ID;
            _applicationName = getMessage(IIQ_APPLICATION_KEY);
        }

        /**
         * This is used when creating new terms.
         */
        public MatchTermDTO(TargetSource targetSource, String type, boolean checkEffective,
                            String matchType) {

            _type = type;
            _checkEffective = checkEffective;
            _permission = MatchTerm.Type.Permission.name().equals(_type);
            setTargetSource(targetSource, matchType);
        }

        /**
         * Set the checkEffective to the given value, and do the same
         * recursively for the children list of MatchTermDTO
         * @param checkEffective
         */
        public void setCheckEffectiveTree(boolean checkEffective) {
            setCheckEffective(checkEffective);
            if (_children != null) {
                for(MatchTermDTO matchTermDTO : _children) {
                    matchTermDTO.setCheckEffectiveTree(checkEffective);
                }
            }
        }
        
        public String getDisplayValue() {
        	if (_displayValue == null) {
        		_displayValue = Explanator.getDisplayValue(_applicationId, _name, _value);
        	}
        	
        	return _displayValue;
        }

        public boolean isContainer() {
            return _container;
        }
        
        public void setContainer(boolean val) {
            _container = val;
        }

        public boolean isCheckEffective() { return _checkEffective; }

        public void setCheckEffective(boolean checkEffective) { _checkEffective = checkEffective; }
        
        public String getOperation() {
            return _operation;
        }
        
        public void setOperation(String val) {
            _operation = val;
        }
        
        public String getOperator() {
            return _operator;
        }
        
        public void setOperator(String val) {
            _operator = val;
            setIsNull(OPERATOR_NULL.equals(val));
            setIsNegative(OPERATOR_NOT_EQ.equals(val));
        }
        
        public void setIsNegative(boolean val) {
            _isNegative = val;
        }
        public boolean getIsNegative() {
            return _isNegative;
        }

        public List<SelectItem> getOperators() {
            if (_matchTermOperators == null) {
                _matchTermOperators = new ArrayList<SelectItem>();
                _matchTermOperators.add(new SelectItem(OPERATOR_EQ, getMessage(MessageKeys.LIST_FILTER_OP_EQUALS)));
                _matchTermOperators.add(new SelectItem(OPERATOR_NOT_EQ, getMessage(MessageKeys.LIST_FILTER_OP_NOTEQUALS)));
                _matchTermOperators.add(new SelectItem(OPERATOR_NULL, getMessage(MessageKeys.SELECTOR_TYPE_IS_NULL)));
            }
            return _matchTermOperators;
        }

        public List<MatchTermDTO> getChildren() {
            return _children;
        }
        
        public void setChildren(List<MatchTermDTO> val) {
            _children = val;
        }
        
        public void addChild(MatchTermDTO val) {
            _children.add(val);
        }
        
        public boolean isSelected() {
            return _selected;
        }

        public void setSelected(boolean b) {
            _selected = b;
        }
        
        public void setIsNull(boolean b) {
            _isNull = b;
            if (_isNull) {
                _value = null;
            }
        }
        
        public boolean getIsNull() {
            return _isNull;
        }

        public String getTargetSourceId() {
            return _targetSourceId;
        }

        public String getTargetSourceName() {
            return _targetSourceName;
        }

        /**
         * This isn't intended for editing, if we do allow it to be
         * changed then we would have to rebuild the allowedNames list!
         */
        public String getApplicationId() {
            return _applicationId;
        }

        public String getApplicationName() {
            return _applicationName;
        }

        /**
         * This isn't intended for editing, if we do allow it to be 
         * changed then we would have to rebuild the allowedNames list!
         */
        public boolean isPermission() {
            return _permission;
        }

        public boolean isRenderAsPermission() {
            return _permission || MatchTerm.Type.Permission.name().equals(_type) ||
                    MatchTerm.Type.TargetPermission.name().equals(_type);
        }

        public String getTypeLabel() {
            if ( _permission || MatchTerm.Type.Permission.name().equals(_type)) {
                return getMessage(MessageKeys.PERMISSION);
            }
            else if (MatchTerm.Type.TargetPermission.name().equals(_type)) {
                return getMessage(MessageKeys.TARGETPERMISSION);
            }
            else if (MatchTerm.Type.RoleAttribute.name().equals(_type)) {
                return getMessage(MessageKeys.ROLE);
            }
            else if (MatchTerm.Type.EntitlementAttribute.name().equals(_type)) {
                return getMessage(MessageKeys.ENTITLEMENT);
            }
            else {
                return getMessage(MessageKeys.ATTRIBUTE);
            }
        }
        
        public String getName() {
            return _name;
        }

        public String getType() {
            return _type;
        }

        public void setType(String type) {
            _type = type;
        }
        
        public void setName(String s) {
            // filter out the empty string names we give to the     
            // initial values like "--Select Attribute--"
            if (s != null && s.trim().length() == 0) s = null;
            _name = s;
        }

        public String getValue() {
            return _value;
        }
        
        public void setValue(String s) {
            //IIQETN-5959 :- Wiping out any dangerous javascript for MatchTerm value.
            //IIQSAW-2748, IIQETN-6252 -- Do NOT use WebUtil.safeHTML(), since it will escape "&", 
            // thus causes double escaping when stored in xml.
            _value = WebUtil.sanitizeHTML(s);
        }

        public List<SelectItem> getAllowedNames() {

            return _allowedNames;
        }

        // allowedNamesTrimmedJSON is used when we don't want the
        // placeholder string for unselected in the list.  Perhaps
        // we should do this in javascript instead of here? -ky

        public String getAllowedNamesTrimmedJSON() {
            List<SelectItem> trimmed = new ArrayList<SelectItem>(_allowedNames);
            trimmed.remove(0);
            return JsonHelper.toJson(trimmed);
        }

        public void setAllowedNamesTrimmedJSON(String ignored) {
            // purposefully empty because it is a calculated value
        }
        
        public boolean isFirst() {
            return _first;
        }
        
        public void setFirst(boolean val) {
            _first = val;
        }
        
        public void unselectAll() {
            _selected = false;
            for (MatchTermDTO child : _children) {
                child.unselectAll();
            }
        }
        
        public void ungroupChildren(List<MatchTermDTO> parentList) {
            
            for (Iterator<MatchTermDTO> iter = _children.iterator(); iter.hasNext();) {
                MatchTermDTO child = iter.next();
                parentList.add(child);
                iter.remove();
            }
        }
        
        public int getDepth() {
            if (!_container) {
                return 1;
            }
            
            int maxChildDepth = 0;
            for (MatchTermDTO child : _children) {
                if (child.getDepth() > maxChildDepth) {
                    maxChildDepth = child.getDepth();
                }
            }

            return maxChildDepth + 1;
        }

        /**
         * We're only doing select lists for schema attributes.
         * In theory if we knew we were dealing with a constraint
         * set of targets we could build a menu for those but there
         * isn't a fast way to get them and there are typically too
         * many to put in a pulldown menu.
         *
         * Look in the account schema for entitlement attrs.  If checkEffective is
         * true, also look in non-account schemas for indexed attrs.
         */
        public List<SelectItem> getAllowedNames(Application app, 
                                                String matchType, boolean checkEffective) {

            Set<String> names = new HashSet<>(); // using a Set to avoid dups

            if (app != null && !_permission) {
                for(Schema schema : Util.safeIterable(app.getSchemas())) {
                    if (schema != null) {
                        if (schema.getObjectType().equals(Connector.TYPE_ACCOUNT)) {
                            // for account schema, find attributes that are entitlements
                            // because it won't be indexed
                            List<AttributeDefinition> atts = schema.getAttributes();
                            if (atts != null) {
                                for (AttributeDefinition att : atts) {
                                    if (matchType == null ||
                                            (MATCH_TYPE_ENTITLEMENT.equals(matchType) &&
                                                    att.isEntitlement())) {
                                        names.add(att.getName());
                                    }
                                }
                            }
                        }
                        else if (checkEffective) {
                            // for non-account schemas, find attributes that are indexed
                            List<AttributeDefinition> atts = schema.getAttributes();
                            if (atts != null) {
                                for (AttributeDefinition att : atts) {
                                    if (matchType == null ||
                                            (MATCH_TYPE_ENTITLEMENT.equals(matchType) &&
                                                    att.isIndexed())) {
                                        names.add(att.getName());
                                    }
                                }
                            }

                        }
                    }
                }
            }

            // could avoid this if we new how to sort SelectItems
            // we have historically sorted these here but now
            // that we're doing it up in setApplication we don't
            // need to
            List<String> nameList = new ArrayList<String>(names);
            Collections.sort(nameList);

            List<SelectItem> items = new ArrayList<SelectItem>();
            for (String name : nameList)
                items.add(new SelectItem(name, name));

            return items;
        }

        /**
         * Return the selectable identity attribute names.
         */
        private List<SelectItem> getIdentityAttributeNames() {
            List<SelectItem> items = null;
            try {
                // use the cache?
                ObjectConfig config = Identity.getObjectConfig();
                if (config != null) {
                    List<ObjectAttribute> atts = config.getObjectAttributes();
                    if (atts != null) {
                        for (ObjectAttribute att : atts) {
                            // !! need a "facets" list so we don't have
                            // to keep overloading the flags and hard
                            // coding names
                            if (att.getName().equals(Identity.ATT_BUNDLES) ||
                                att.getName().equals(Identity.ATT_ASSIGNED_ROLES) ||
                                att.getName().equals(Identity.ATT_MANAGER) ||
                                att.getName().equals(Identity.ATT_INACTIVE) ||
                                att.getName().equals(Identity.ATT_CAPABILITIES) ||
                                att.getName().equals(Identity.ATT_MANAGER_STATUS) ||
                                att.getName().equals(Identity.ATT_RIGHTS) ||
                                att.getName().equals(Identity.ATT_WORKGROUPS) ||
                                !att.isSystem()) {

                                if (items == null)
                                    items = new ArrayList<SelectItem>();
                                String name = att.getName();
                                String dname = att.getDisplayName();
                                if (dname == null)
                                    dname = name;
                                else 
                                    dname = getMessage(dname);
                                items.add(new SelectItem(name, dname));
                            }
                        }
                    }
                }
            }
            catch (Throwable t) {
                // hmm, I guess it's better to give them a text box 
                // than die?
            }
            return items;
        }
        
        /**
         * Return the selectable role attribute names.
         */
        private List<SelectItem> getRoleAttributeNames() {
            List<SelectItem> items = new ArrayList<SelectItem>();
            try {
                ObjectConfig config = Bundle.getObjectConfig();
                if (config != null) {
                    List<ObjectAttribute> atts = config.getObjectAttributes();
                    if (atts != null) {
                        for (ObjectAttribute att : atts) {
                            //adding all searchable extended attributes
                            if (att.isExtended()) {
                                //only support boolean and string type
                                //null type means string
                                if (att.getType() == null ||
                                        ObjectAttribute.TYPE_STRING.equals(att.getType()) ||
                                        ObjectAttribute.TYPE_BOOLEAN.equals(att.getType())) {
                                    String name = att.getName();
                                    String dname = att.getDisplayName();
                                    if (dname == null)
                                        dname = name;
                                    else 
                                        dname = getMessage(dname);
                                    items.add(new SelectItem(name, dname));
                                }
                            }
                        }
                    }
                }
                
                //additional attributes not in ObjectConfig
                for (RoleAttributes att : RoleAttributes.values()) {
                    items.add(new SelectItem(att.getName(), att.getLocalizedMessage(getLocale(), getUserTimeZone())));
                }
            }
            catch (Throwable t) {
                // hmm, I guess it's better to give them a text box 
                // than die?
            }
            return items;
        }

        /**
         * Return the selectable ManagedAttribute attribute names.
         */
        private List<SelectItem> getEntitlementAttributeNames() {
            List<SelectItem> items = new ArrayList<SelectItem>();;
            try {
                ObjectConfig config = ManagedAttribute.getObjectConfig();
                if (config != null) {
                    List<ObjectAttribute> atts = config.getObjectAttributes();
                    if (atts != null) {
                        for (ObjectAttribute att : atts) {
                            //adding all searchable extended attributes
                            if (att.isExtended()) {
                                //only support boolean and string type
                                //null type means string
                                if (att.getType() == null ||
                                        ObjectAttribute.TYPE_STRING.equals(att.getType()) ||
                                        ObjectAttribute.TYPE_BOOLEAN.equals(att.getType())) {
                                    String name = att.getName();
                                    String dname = att.getDisplayName();
                                    if (dname == null)
                                        dname = name;
                                    else 
                                        dname = getMessage(dname);
                                    items.add(new SelectItem(name, dname));
                                }
                            }
                        }
                    }
                    //additional attributes not in ObjectConfig
                    for (EntitlementAttributes att : EntitlementAttributes.values()) {
                        items.add(new SelectItem(att.getName(), att.getLocalizedMessage(getLocale(), getUserTimeZone())));
                    }
                }
            }
            catch (Throwable t) {
                // hmm, I guess it's better to give them a text box 
                // than die?
            }
            return items;
        }

        /**
         * Create a MatchTerm from the DTO.
         * Return null if there is nothing of interest in here so 
         * we can filter them from the MatchExpression.
         */
        public MatchTerm convert() throws GeneralException {

            if (!_container) {
                return convertLeaf();
            }
            
            MatchTerm term = new MatchTerm();
            term.setContainer(true);
            term.setAnd(_operation.equals(OP_AND));
            
            for (MatchTermDTO child : _children) {
                term.addChild(child.convert());
            }
            
            return term;
        }

        private MatchTerm convertLeaf() throws GeneralException {

            MatchTerm term = null;
            
            if (_name != null) {
                term = new MatchTerm();

                if (_applicationId != null &&
                    !_applicationId.equals(IIQ_APPLICATION_ID)) {
                    Application app = resolveById(Application.class, _applicationId);
                    if (app == null) {
                        // should only happen if this was deleted out
                        // from under us during editing
                        //throw new GeneralException(new Message(Messages.POLICY_INVALID_APPLICATION, _application));
                        throw new GeneralException(new Message("policy_invalid_application", _applicationId));
                    } else {
                        app.load();
                        term.setApplication(app);
                    }
                }
                else if (_targetSourceId != null) {
                    TargetSource targetSource = resolveById(TargetSource.class, _targetSourceId);
                    if (targetSource == null) {
                        // should only happen if this was deleted out
                        // from under us during editing
                        throw new GeneralException(new Message("policy_invalid_target_source", _targetSourceId));
                    } else {
                        targetSource.load();
                        term.setTargetSource(targetSource);
                    }
                }


                term.setPermission(_permission);
                term.setName(_name);
                if (_isNull) {
                    term.setValue(null);
                } else {
                    term.setValue(_value);
                }

                term.setNegative(_isNegative);
                
                if (_type != null) {
                    MatchTerm.Type type = MatchTerm.Type.valueOf(_type);
                    term.setType(type);
                }

                term.setCheckEffective(_checkEffective);
                if (_contributingEntitlements != null) {
                    List<MatchTerm.ContributingEntitlement> ces = new ArrayList<MatchTerm.ContributingEntitlement>();
                    for(ContributingEntitlementDTO ceDTO : _contributingEntitlements) {
                        ces.add(ceDTO.convert());
                    }
                }
            }
            
            return term;
        }

        public String render() {
            StringBuilder builder = new StringBuilder();
            
            if (!_container) {
                return MatchTerm.renderLeaf(_type, _name, _value, _isNegative);
            }
            
            Iterator<MatchTermDTO> it = _children.iterator();
            while (it.hasNext()) {
                builder.append("(" + it.next().render() + ")");
                if (it.hasNext()) {
                    //localize "and"/"or"?, maybe not... queries are not language specific I think
                    builder.append(" " + (_operation.equals(OP_AND) ? "AND" : "OR") + " ");
                }
            }
            
            return builder.toString();
        }

        public static class ContributingEntitlementDTO {
            //Application Name
            String source;
            //TargetType
            String type;
            //Hierarchy
            String path;

            public ContributingEntitlementDTO(MatchTerm.ContributingEntitlement ce) {
                setSource(ce.getSource());
                setType(ce.getType());
                setPath(ce.getPath());
            }

            public MatchTerm.ContributingEntitlement convert() {
                MatchTerm.ContributingEntitlement ce = new MatchTerm.ContributingEntitlement();
                ce.setSource(getSource());
                ce.setType(getType());
                ce.setPath(getPath());
                return ce;
            }

            public String getSource() {
                return source;
            }
            public void setSource(String source) {
                this.source = source;
            }

            public String getType() {
                return type;
            }
            public void setType(String type) {
                this.type = type;
            }

            public String getPath() {
                return path;
            }
            public void setPath(String path) {
                this.path = path;
            }
        }
        
    }

    public boolean isPlaceholder() {
        refreshSummary();
        String summary = getSummary().trim();
        return (summary == null || summary.length() == 0);
    }
}
