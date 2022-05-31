/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Dictionary;
import sailpoint.object.DictionaryTerm;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

/**
 * This class checks password to see if it contains any of the words in the 
 * password dictionary as substrings.  
 * 
 */

public class PasswordConstraintDictionary extends AbstractPasswordConstraint {

    private static Map<String,Dictionary> _dictionaryCache = null;
    Dictionary _dictionary = null;
    private static Log log = LogFactory.getLog(PasswordConstraintDictionary.class);

    public PasswordConstraintDictionary(Dictionary dictionary) {
        _dictionary = dictionary;
	}

    @Override
    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {
        Meter.enterByName("PasswordConstraintDictionary.validate()");
        /** Load the list of dictionary terms and compare this password against any of them **/
        if (_dictionary != null) {
            //If attempting to use the cache causes the error,
            //we will just use whatever _dictionary was passed-in.
            try {
                checkAndResetUpdatedCacheDictionary();
            } catch (GeneralException ge) {
                log.error("Unable to use dictionary cache!", ge);
            }
            
            List<DictionaryTerm> terms = _dictionary.getTerms();
            for(DictionaryTerm term : terms) {
                if(password.toLowerCase().matches(".*"+term.getValue().toLowerCase()+".*")) {
                    Meter.exitByName("PasswordConstraintDictionary.validate()");
                    throw new PasswordPolicyException(MessageKeys.PASSWD_INVALID_TERM);
                }
            }

        }
        Meter.exitByName("PasswordConstraintDictionary.validate()");
        return false;
    }
    
    private void checkAndResetUpdatedCacheDictionary() throws GeneralException {
        if(_dictionaryCache == null) {
            _dictionaryCache = new HashMap<String,Dictionary>();
        }
        
        if(_dictionary != null) {
            //See if we need to reload this particulat cached dictionary based on the last modification time
            if(!_dictionaryCache.containsKey( _dictionary.getName())) {
                _dictionaryCache.put(_dictionary.getName(), _dictionary);
            } else {
                //reset the property to the cached version unless the new version
                //is more current based on modified time
                Dictionary dictionary = _dictionaryCache.get(_dictionary.getName());
                if(!Util.nullSafeEq(dictionary.getModified(), _dictionary.getModified())) {
                    _dictionaryCache.put(_dictionary.getName(), _dictionary);
                } else {
                    _dictionary = dictionary;
                }
            }
        }
    }
}
