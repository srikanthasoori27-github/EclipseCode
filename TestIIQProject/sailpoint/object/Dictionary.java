/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author peter.holcomb
 */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;
/**
 * Used to maintain a collection of words that should
 * not be allowed as passwords.
 */
public class Dictionary extends SailPointObject {
    
    /**
     * The name of the singleton system configuration object.
     */
    public static final String OBJ_NAME = "PasswordDictionary";
    
    String name;

    List<DictionaryTerm> terms;

    public Dictionary() {
        super();
    }
    
    public DictionaryTerm getTerm(String value) {
        DictionaryTerm found = null;
        if (terms != null && value != null) {
            for (DictionaryTerm term : Util.safeIterable(terms)) {
                if (term != null && term.getValue() != null && term.getValue().equalsIgnoreCase(value)) {
                    found = term;
                    break;
                }
            }
        }
        return found;
    }
    
    public List<DictionaryTerm> getTerms() {
        return terms;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public void setTerms(List<DictionaryTerm> terms) {
        this.terms = terms;
    }

    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
