/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import java.util.List;

import sailpoint.api.EncodingUtil;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

/**
 * This class compares the desired password to the elements of the history.
 * We could avoid decryption if instead we encrypted the desired password 
 * using the key of each history element then compare the encrypted result.!!  
 * This will require however that add methods to the SailPointConsole 
 * interface, perhaps compareEncrypted(s2, s2)?
 * 
 * The current history depth is passed so it can be lowered without needing 
 * to go prune every saved history list. Just ignore the ones that aren't 
 * relevant any more, eventually Identitizer will prune it.
 *
 */

public class PasswordConstraintHistory extends AbstractPasswordConstraint {

    public static final String HISTORY = "passwordHistory";
    public static final String HISTORY_MAX = "passwordHistoryMax";
    public static final String TRIVIALITY_CHECK = "checkPasswordTriviality";
    public static final String MIN_HISTORY_UNIQUECHARS = "minHistoryUniqueChars";
    public static final String CASESENSITIVITY_CHECK = "checkCaseSensitive";

    private int _historyDepth = 0;
    private List<String> _history;
    private boolean _checkTriviality = false;

    int minHistoryUniqueCount = 0;
    boolean isCaseSensitive = false;

    public PasswordConstraintHistory(int historyDepth, List<String> history) {
        _historyDepth = historyDepth;
        _history = history;
    }
    public void setTriviality(boolean value) {
        _checkTriviality = value;
    }

    public void setMinHistoryUniqueCount(int count) {
        minHistoryUniqueCount = count;
    }

    public void setCaseSensitivityCheck(boolean value) {
        isCaseSensitive = value;
    }

    public boolean isValidForHashing() {
        return !_checkTriviality && !isCaseSensitive && minHistoryUniqueCount == 0;
    }

    @Override
    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {

        // even though we have only one key and could use contains()
        // set it up the way it will eventually need to work
        if (_history != null && password != null) {
            for (int i = 0 ; i < _history.size() ; i++) {
                if (i < _historyDepth) {
                    String text = _history.get(i);
                    SailPointContext context = null;

                    try {
                        context = SailPointFactory.getCurrentContext();
                    } catch (GeneralException e) {
                        throw new PasswordPolicyException(e.getMessage());
                    }

                    if (EncodingUtil.isHashed(text)) {
                        //for hashed password, only support exact match
                        if (EncodingUtil.isMatch(password, text, context)) {
                            throw new PasswordPolicyException(MessageKeys.PASSWD_HISTORY_CONFLICT);
                        } else {
                            continue;
                        }
                    } else {
                        //encrypted
                        try {
                            text = context.decrypt(text);
                        } catch (GeneralException e) {
                            throw new PasswordPolicyException(e.getMessage());
                        }
                        if (_checkTriviality) {
                            if (text.toUpperCase().contains(password.toUpperCase()) || 
                                    password.toUpperCase().contains(text.toUpperCase())) {
                                throw new PasswordPolicyException(MessageKeys.PASSWD_HISTORY_CONFLICT);
                            }
                        } else {
                            if (password.equals(text)) {
                                throw new PasswordPolicyException(MessageKeys.PASSWD_HISTORY_CONFLICT);
                            }
                        }
                        if (minHistoryUniqueCount > 0)
                        {
                            for(int k = 0; k<text.length()-minHistoryUniqueCount+1;k++) {
                                char[] temp = new char[minHistoryUniqueCount];
                                text.getChars(k, k+minHistoryUniqueCount,temp ,0);
                                String chk_temp = new String(temp);
                                if (isCaseSensitive) {
                                    if (password.contains(chk_temp)) {
                                        throw new PasswordPolicyException(MessageKeys.PASSWD_HISTORY_CONFLICT);
                                    }
                                } else {
                                    if (password.toUpperCase().contains(chk_temp.toUpperCase())) {
                                        throw new PasswordPolicyException(MessageKeys.PASSWD_HISTORY_CONFLICT);
                                    }
                                }
                            }
                        }
                    }
                } else 
                    break;
            }
        }
        return true;
    }
}
