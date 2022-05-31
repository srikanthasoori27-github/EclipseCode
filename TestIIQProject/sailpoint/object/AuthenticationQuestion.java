/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * An authentication question is a question that can be answered by users that
 * are attempting to authenticate without a password (for example, when a password is
 * forgotten).
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@XMLClass
public class AuthenticationQuestion extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * The question text or a message key with the question text.
     */
    private String question;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public AuthenticationQuestion() {
        super();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // SailPointObject overrides
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    public boolean hasName() {
        return false;
    }

    @Override
    public String[] getUniqueKeyProperties() {
        return new String[] { "question" };
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitAuthenticationQuestion(this);
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("question", "Question");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s\n";
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @XMLProperty
    public String getQuestion() {
        return this.question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }

    /**
     * Return the localized question if the question is a message key, otherwise
     * just return the question.
     * 
     * @param  locale  The Locale to use for localization.
     */
    public String getQuestion(Locale locale) {
        String i18n = Internationalizer.getMessage(this.question, locale);
        return (null != i18n) ? i18n : this.question;
    }
}
