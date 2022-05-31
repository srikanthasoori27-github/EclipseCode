/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author peter.holcomb
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLProperty;

/**
 * Part of the <code>Dictionary</code> model, used to maintain
 * a collection of words that should not be used as passwords.
 */
public class DictionaryTerm extends SailPointObject {

    String value;
    
    /** Owning dictionary **/
    Dictionary dictionary;
    
    public DictionaryTerm() {
        super();
    }

    @XMLProperty
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }

    public Dictionary getDictionary() {
        return dictionary;
    }

    public void setDictionary(Dictionary dictionary) {
        this.dictionary = dictionary;
    }
    
    @Override
    public boolean hasName() {
        return false;
    }
}
