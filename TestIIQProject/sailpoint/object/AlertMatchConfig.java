/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ryan.pickens on 6/22/16.
 */
@XMLClass
public class AlertMatchConfig extends AbstractXmlObject {

    public AlertMatchConfig() {

    }

    AlertMatchExpression _matchExpression;

    Rule _matchRule;

    //TODO: Script/Filter?

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public AlertMatchExpression getMatchExpression() {
        return _matchExpression;
    }

    public void setMatchExpression(AlertMatchExpression _matchExpression) {
        this._matchExpression = _matchExpression;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getMatchRule() {
        return _matchRule;
    }

    public void setMatchRule(Rule _matchRule) {
        this._matchRule = _matchRule;
    }
    
    public void load() {
        if (_matchExpression != null) _matchExpression.load();
        
        if (_matchRule != null) _matchRule.load();
    }


    @XMLClass
    public static class AlertMatchTerm extends AbstractXmlObject {

        public AlertMatchTerm() {

        }

        /**
         * Source of the Alert. This is optional
         */
        Application _source;

        /**
         * Name of the Alert attribute
         */
        String _name;

        /**
         * Value of the Alert attribute
         */
        String _value;

        boolean _container;
        boolean _and;
        List<AlertMatchTerm> _children;

        @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="SourceRef")
        public Application getSource() {
            return _source;
        }

        public void setSource(Application _source) {
            this._source = _source;
        }

        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String _name) {
            this._name = _name;
        }

        @XMLProperty
        public String getValue() {
            return _value;
        }

        public void setValue(String _value) {
            this._value = _value;
        }

        @XMLProperty
        public boolean isContainer() { return _container; }

        public void setContainer(boolean b) { _container = b; }

        @XMLProperty
        public boolean isAnd() { return _and; }

        public void setAnd(boolean b) { _and = b; }

        @XMLProperty(mode = SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<AlertMatchTerm> getChildren() { return _children; }

        public void setChildren(List<AlertMatchTerm> children) { _children = children; }

        public void addChild(AlertMatchTerm term) {
            if (term != null) {
                if (_children == null) {
                    _children = new ArrayList<AlertMatchTerm>();
                }
                _children.add(term);
            }
        }

        /**
         * Load the term and all term children
         */
        public void load() {
            if (_source != null) {
                _source.load();
            }

            for (AlertMatchTerm term : Util.safeIterable(_children)) {
                term.load();
            }
        }


        public boolean match(Alert alert) {

            if (!_container) {
                return matchLeaf(alert);
            }

            boolean match = true;
            for (AlertMatchTerm term : _children) {
                match = term.match(alert);
                if ((!match && _and) || (match && !this._and)) {
                    break;
                }
            }


            return match;
        }

        private boolean matchLeaf(Alert a) {
            boolean match = true;
            if (_source != null) {
                if (a.getSource() != null) {
                    match &= Util.nullSafeEq(a.getSource().getName(), _source.getName());
                } else {
                    //If no source on the matchTerm, no match
                    match = false;
                }
            }

            match &= Util.nullSafeEq(_value, a.getAttribute(_name));

            return match;
        }


    }

    /**
     * Expression to match alerts. This will be composed of
     */
    @XMLClass
    public static class AlertMatchExpression extends AbstractXmlObject {
        List<AlertMatchTerm> _matchTerms;
        //True to and the terms
        boolean _and;

        @XMLProperty(mode= SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<AlertMatchTerm> getMatchTerms() { return _matchTerms; }

        public void setMatchTerms(List<AlertMatchTerm> terms) { _matchTerms = terms; }

        public void addMatchTerm(AlertMatchTerm term) {
            if (term != null) {
                if (_matchTerms == null) {
                    _matchTerms = new ArrayList<AlertMatchTerm>();
                }
                _matchTerms.add(term);
            }
        }

        @XMLProperty
        public boolean isAnd() { return _and; }

        public void setAnd(boolean b) { _and = b; }

        public void load() {
            for (AlertMatchTerm term : Util.safeIterable(_matchTerms)) {
                term.load();
            }
        }

        public boolean match(Alert alert) {
            boolean match = false;

            if (Util.isEmpty(_matchTerms)) {
                //Nothing to match, return false;
            } else {
                for (AlertMatchTerm term : Util.safeIterable(_matchTerms)) {
                    match = term.match(alert);
                    if ((!match && _and) || (match && !this._and)) {
                        break;
                    }
                }
            }

            return match;
        }

    }
}
