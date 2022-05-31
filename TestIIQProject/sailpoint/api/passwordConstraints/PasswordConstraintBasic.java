/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * This is password constraint class for Alphanumeric constraint
 * It checks password against min/max/alpha/numeric/upper/lower/special 
 * constraints. Any further extensions to these basic constraints should 
 * be added here. We can also think of refactoring this class into separate 
 * MinMax/Alpha/Numeric/Upper/Lower/Special classes. 
 * 
 * This class also contains logic to auto generate password based defined 
 * constraints.
 *  
 * @author ketan.avalaskar
 *
 */

public class PasswordConstraintBasic extends AbstractPasswordConstraint {

    private PasswordPolicyException _currentException = null;

    public static final String MIN_LENGTH = "passwordMinLength";
    public static final String MAX_LENGTH = "passwordMaxLength";
    public static final String MIN_ALPHA = "passwordMinAlpha";
    public static final String MIN_NUMERIC = "passwordMinNumeric";
    public static final String MIN_UPPER = "passwordMinUpper";
    public static final String MIN_LOWER = "passwordMinLower";
    public static final String MIN_SPECIAL = "passwordMinSpecial";
    public static final String MIN_CHARTYPE = "passwordMinCharType";
    // current character types - upper/lower/digit/special
    public static final int MAXIMUM_CHARACTER_TYPES = 4;

    // Password Generator Fields 

    private static final int      DEFAULT_LENGTH = 10;

    private static final String UPPER_SET_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER_SET_STRING = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBER_SET_STRING = "0123456789";
    /**
     * The characters we consider "special".
     */
    public static final String DEFAULT_SPECIAL_SET_STRING = "~!@#$%^&*()_+`-={}|\\][:\"\';<>?,.";

    private static final char[] DEFAULT_SPECIAL_SET = DEFAULT_SPECIAL_SET_STRING.toCharArray();

    // This string may hold default set or allowable set defined in system password policy page
    private String allowableUpper=null;
    private String allowableLower=null;
    private String allowableDigits=null;
    private String allowableSpecial=null;

    // Base on this flag, validation happens against defined set or 
    // default java functions like isUpper etc.
    boolean isCustomUpperSet = false;
    boolean isCustomLowerSet = false;
    boolean isCustomNumberSet = false;

    private Configuration _config;

    public int _numAlpha = 0;
    public int _numNumeric = 0;
    public int _numUpper = 0;
    public int _numLower = 0;
    public int _numSpecial = 0;

    private int _minCharType = 0;
    private int _minLength = 0;
    private int _maxLength = 0;
    private int _minAlpha = 0;
    private int _minNumeric = 0;
    private int _minUpper = 0;
    private int _minLower = 0;
    private int _minSpecial = 0;

    /**
     * Define character sets for different types base on systemConfig 
     */
    private void defineCharacterSets() {
        String customUpper = _config.getString(Configuration.PASSWORD_UPPER_CHARACTERS);
        if(Util.isNotNullOrEmpty(customUpper)) {
            allowableUpper = customUpper;
            isCustomUpperSet = true;
        } else {
            allowableUpper = UPPER_SET_STRING;
        }

        String customLower = _config.getString(Configuration.PASSWORD_LOWER_CHARACTERS);
        if(Util.isNotNullOrEmpty(customLower)) {
            allowableLower = customLower;
            isCustomLowerSet = true;
        } else {
            allowableLower = LOWER_SET_STRING;
        }

        String customNumber = _config.getString(Configuration.PASSWORD_NUMBER_CHARACTERS);
        if(Util.isNotNullOrEmpty(customNumber)) {
            allowableDigits = customNumber;
            isCustomNumberSet = true;
        } else {
            allowableDigits = NUMBER_SET_STRING;
        }

        String customSpecial = _config.getString(Configuration.PASSWORD_SPECIAL_CHARACTERS);
        if(Util.isNotNullOrEmpty(customSpecial)) {
            allowableSpecial = customSpecial;
        } else {
            allowableSpecial = DEFAULT_SPECIAL_SET_STRING;
        }
    }

    public PasswordConstraintBasic(Configuration config) {
        _config = config;
        defineCharacterSets();
    }

    public void setMinLength(int min) {
        _minLength = min;
    }

    public void setMaxLength(int max) {
        _maxLength = max;
    }

    public void setMinAlpha(int alpha) {
        _minAlpha = alpha;
    }

    public void setMinNumeric(int numeric) {
        _minNumeric = numeric;
    }

    public void setMinUpper(int upper) {
        _minUpper = upper;
    }

    public void setMinLower(int lower) {
        _minLower = lower;
    }

    public void setMinSpecial(int special) {
        _minSpecial = special;
    }

    public void setMinCharType(int reqType) {
        _minCharType = reqType;
    }

    /**
     * Populates a bunch of properties based on a password
     * @param password The password to analyze
     * @throws GeneralException If unable to get <code>SystemConfig</code> or if <code>password</code> contains invalid characters.
     */
    protected void analyzePasswordStats(String password) throws PasswordPolicyException {

        if (password == null) {
            // no stats, shouldn't have made it here
            return;
        }
        StringBuilder invalidCharacters = new StringBuilder();
        for (int i = 0 ; i < password.length(); i++) {
            char ch = password.charAt(i);

            // Each character will be checked against either custom set defined in systemConfig
            // or against default java functions
            if(Character.isLetter(ch)) {
                _numAlpha++;
                // custom upper set defined in systemConfig
                if (isCustomUpperSet) {
                    if (allowableUpper.indexOf((int)ch) >= 0) {
                        _numUpper++;
                        continue;
                    }
                } else if (Character.isUpperCase(ch)) {
                    _numUpper++;
                    continue;
                } 

                // custom lower set defined in systemConfig
                if(isCustomLowerSet) {
                    if( allowableLower.indexOf((int)ch) >= 0) {
                        _numLower++;
                        continue;
                    }
                } else {
                    // for backward compatibility, we are not checking against 
                    // Character.isLowerCase. So default lower set will include all nonUpper 
                    // characters e.g. foreign characters like katakana script etc.
                    _numLower++;
                    continue;
                }
            }

            if(isCustomNumberSet) {
                if (allowableDigits.indexOf((int)ch) >= 0) {
                    _numNumeric++;
                    continue;
                }
            } else {
                if(Character.isDigit(ch)) {
                    _numNumeric++;
                    continue;
                }
            }

            if (allowableSpecial.indexOf((int)ch) >= 0) {
                _numSpecial++;
                continue;
            }

            if(invalidCharacters.indexOf(String.valueOf(ch)) < 0) {
                invalidCharacters.append(ch);
            }
        }
        if(invalidCharacters.length() != 0) {
            throw new PasswordPolicyException(new Message(Message.Type.Error,
                    MessageKeys.PASSWD_CHECK_INVALID_CHARACTER,invalidCharacters.toString()));
        }
    }

    private void addExceptionMessage(Message message) {
        if (_currentException == null) {
            _currentException = new PasswordPolicyException(message);
        } else {
            _currentException.addMessage(message);
        }
    }

    private void addExceptionMessage(boolean isMax, int number, String type) {
        addExceptionMessage(PasswordPolicyException.createMessage(isMax, number, type));
    }

    /**
     * Assuming that password stats analyzed first and validation min/max stats defined.
     * @throws PasswordPolicyException 
     * 
     */
    @Override
    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {

        analyzePasswordStats(password);

        int passwordLength = password.length();

        if (_minLength > 0 && passwordLength < _minLength) {
            addExceptionMessage(PasswordPolicyException.MIN, _minLength, 
                    PasswordPolicyException.CHARS);
        }

        if (_maxLength > 0 && passwordLength > _maxLength) {
            addExceptionMessage(PasswordPolicyException.MAX, _maxLength, 
                    PasswordPolicyException.CHARS);
        }

        if (_minAlpha > 0 && _numAlpha < _minAlpha) {
            addExceptionMessage(PasswordPolicyException.MIN, _minAlpha, 
                    PasswordPolicyException.LETTERS);
        }

        if (_minCharType > 0) {
            int count = 0;
            // Hang onto any error messages from constraints that have failed so we can add them to the
            // exception messages if the validation of minCharType fails
            List<Message> messages = new ArrayList<Message>();
            if (_minUpper > 0) {
                if (_numUpper >= _minUpper) {
                    count++;
                } else {
                    messages.add(PasswordPolicyException.createMessage(PasswordPolicyException.MIN, _minUpper,
                            PasswordPolicyException.UCASE));
                }
            }
            if (_minLower > 0) {
                if (_numLower >= _minLower) {
                    count++;
                } else {
                    messages.add(PasswordPolicyException.createMessage(PasswordPolicyException.MIN, _minLower,
                            PasswordPolicyException.LCASE));
                }
            }
            if (_minNumeric > 0) {
                if (_numNumeric >= _minNumeric) {
                    count++;
                } else {
                    messages.add(PasswordPolicyException.createMessage(PasswordPolicyException.MIN, _minNumeric,
                            PasswordPolicyException.DIGITS));
                }
            }
            if (_minSpecial > 0) {
                if (_numSpecial >= _minSpecial) {
                    count++;
                } else {
                    messages.add(PasswordPolicyException.createMessage(PasswordPolicyException.MIN, _minSpecial,
                            PasswordPolicyException.SPECIAL_CHARS));
                }
            }
            if (count < _minCharType) {
                addExceptionMessage(new Message(Message.Type.Error,
                        MessageKeys.PASSWD_MIN_CHAR_TYPE, _minCharType));

                // If the validation failed, make sure to add any of the related failure errors to the list
                // that is returned to the user
                for(Message message : messages) {
                    addExceptionMessage(message);
                }
            }
        } else {
            if (_minNumeric > 0 && _numNumeric < _minNumeric) {
                addExceptionMessage(PasswordPolicyException.MIN, _minNumeric,
                        PasswordPolicyException.DIGITS);
            }
            if (_minUpper > 0 && _numUpper < _minUpper) {
                addExceptionMessage(PasswordPolicyException.MIN, _minUpper,
                        PasswordPolicyException.UCASE);
            }
            if (_minLower > 0 && _numLower < _minLower) {
                addExceptionMessage(PasswordPolicyException.MIN, _minLower,
                        PasswordPolicyException.LCASE);
            }
            if (_minSpecial > 0 && _numSpecial < _minSpecial) {
                addExceptionMessage(PasswordPolicyException.MIN, _minSpecial,
                        PasswordPolicyException.SPECIAL_CHARS);
            }
        }

        if (_currentException != null) {
            throw _currentException;
        }

        return true;
    }

    // Password Generator Methods

    public static char[] getDefaultSpecialSet() {
        return DEFAULT_SPECIAL_SET;
    }

    private int getVarietyCount() {
        int varietyCount = 0;
        if (_minUpper != 0) {
            varietyCount++;
        }
        if (_minLower != 0) {
            varietyCount++;
        }
        if (_minAlpha != 0) {
            varietyCount++;
        }
        if (_minNumeric != 0) {
            varietyCount++;
        }
        if (_minSpecial != 0) {
            varietyCount++;
        }
        return varietyCount;
    }

    private int calculateRequiredPasswordLength() {
        int length = 0;
        if (_maxLength == 0 && _minLength == 0) {
            // No min max requirements
            length = DEFAULT_LENGTH;
        }
        else if (_maxLength != 0) {
            length = _maxLength;
        }
        else if (_minLength != 0 && _maxLength == 0) {
            if (_minLength > DEFAULT_LENGTH)
                length = _minLength;
            else
                length = DEFAULT_LENGTH;
        }
        else {
            length = 0; // shouldnt get here
        }
        return length;
    }

    /**
     * 
     * @return
     */
    private String getVarietyChars() {
        char[] upperSet = allowableUpper.toCharArray();
        char[] lowerSet = allowableLower.toCharArray();
        char[] alphaSet =  (allowableUpper + allowableLower).toCharArray();
        char[] digitsSet =  allowableDigits.toCharArray();
        char[] specialSet = allowableSpecial.toCharArray();

        StringBuilder vcharSet = new StringBuilder();

        int length = 0;

        if (_minUpper != 0) {
            length = _minUpper;
            while (length-- > 0) {
                vcharSet.append(getRandomFromSet(upperSet) + "");
            }
        }

        if (_minLower != 0) {
            length = _minLower;
            while (length-- > 0) {
                vcharSet.append(getRandomFromSet(lowerSet) + "");
            }
        }

        if (_minAlpha != 0) { 
            length = _minAlpha - (_minUpper + _minLower);
            while (length-- > 0) {
                vcharSet.append(getRandomFromSet(alphaSet) + ""); // this may try to add duplicate
            }
        }

        if (_minNumeric != 0) {
            length = _minNumeric;
            while (length-- > 0) {
                vcharSet.append(getRandomFromSet(digitsSet) + "");
            }
        }

        if (_minSpecial != 0) {
            length = _minSpecial;
            while (length-- > 0) {
                vcharSet.append(getRandomFromSet(specialSet) + "");
            }
        }

        return vcharSet.toString();
    }

    /**
     * Mix the password
     * 
     * @param password
     * @return
     */
    private String mixString(String password) {

        char[] charPass = password.toCharArray();

        List<Character> passArray = new ArrayList<Character>(charPass.length);

        for (int i=0; i<charPass.length; ++i) {
            passArray.add(charPass[i]);
        }

        Collections.shuffle(passArray, getRandom());

        java.util.Iterator<Character> posIt = passArray.iterator();

        StringBuilder sb = new StringBuilder();

        while (posIt.hasNext()) {
            sb.append(posIt.next());
        }

        return sb.toString();
    }

    @Override
    public String generate() throws GeneralException {
        initRandom();

        int length = calculateRequiredPasswordLength();

        int varietyCount = getVarietyCount();

        // Variety count may not be larger than maxLength
        if (_maxLength != 0 && varietyCount > _maxLength) {
            // We won't be able to fulfill this request
            // This shouldn't happen since the password policy should be checked 
            // for this condition before it's saved but if one were to manually 
            // set this condition we should probably throw instead of returning null.
            throw new GeneralException(MessageKeys.PASSWD_GENERATOR_IMPOSSIBLE_POLICY);
        }

        StringBuilder sb = new StringBuilder();
        String varietychars = "";
        if (varietyCount > 0) {
            varietychars = getVarietyChars();
        }

        int lengthLeft = length - varietychars.length();
        char[] allSet =  (allowableUpper + allowableLower + allowableDigits + allowableSpecial).toCharArray();
        while (lengthLeft-- > 0) {
            sb.append(getRandomFromSet(allSet));
        }

        String password = sb.toString();
        return mixString(password+varietychars);
    }	
}
