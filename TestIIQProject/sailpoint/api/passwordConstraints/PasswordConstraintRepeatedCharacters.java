package sailpoint.api.passwordConstraints;

import java.util.HashSet;
import java.util.Set;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.web.messages.MessageKeys;

public class PasswordConstraintRepeatedCharacters extends AbstractPasswordConstraint {

    public static final String REPEATED_CHARACTERS = "passwordRepeatedChar";

    private int _allowedRepeatedCharacters = 0;

    public PasswordConstraintRepeatedCharacters( int allowedRepetedCharacters ) {
        _allowedRepeatedCharacters = allowedRepetedCharacters;
    }

    @Override
    public boolean validate(SailPointContext ctx, String password ) throws PasswordPolicyException {

        if ( password != null ) {
            char pwd[] = password.toCharArray();
            Set<Character> match = new HashSet<Character> ();
            int check = 0;

            for ( int i=0; i < pwd.length; i++ ) {
                int repeatedChars = 1;
                if ( !match.add(pwd[i]) ) {
                    if ( pwd[i-1] == pwd[i] ) {
                        // validated the password for the consecutive occurrences of repeated characters
                        // if maximum number of allowed repeated characters is 2 then
                        // password 'cccloud' fails where 'ccloud' passes.
                        int j = i;
                        // check current character with previous character in the password
                        while (j>0 && pwd[j-1] == pwd[j]) {
                            // count to check consecutive repeated characters
                            repeatedChars++;
                            j--;
                            if ( repeatedChars > _allowedRepeatedCharacters) {
                                throw new PasswordPolicyException( new Message(Type.Error, MessageKeys.PASSWD_CHECK_REPEATED_CONSECUTIVE_CHARS, 
                                        _allowedRepeatedCharacters ));
                            }
                        }
                        // Validate password for instances of repeated character in the password
                        // if maximum allowed repeated characters are set to 2 then 
                        // password 'cclooudd' is denied where password 'cclooud' is allowed
                        check++;
                        if ( check > _allowedRepeatedCharacters ) {
                            throw new PasswordPolicyException( new Message(Type.Error, MessageKeys.PASSWD_CHECK_REPEATED_CHARS, 
                                    _allowedRepeatedCharacters ));
                        }
                    }
                }
            }
        }
        return true;
    }
}
