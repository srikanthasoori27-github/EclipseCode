/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * An answer to an authentication question for an identity.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@XMLClass @SuppressWarnings("serial")
public class AuthenticationAnswer extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private Identity identity;
    private AuthenticationQuestion question;
    private String answer;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public AuthenticationAnswer() {
        super();
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // SailPointObject overrides
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitAuthenticationAnswer(this);
    }

    @Override
    public boolean hasName() {
        return false;
    }
    
    @Override
    public boolean hasAssignedScope() {
        return false;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    // Similar to Link, the identity is not stored in the XML.
    public Identity getIdentity() {
        return this.identity;
    }
    
    public void setIdentity(Identity identity) {
        this.identity = identity;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="QuestionRef")
    public AuthenticationQuestion getQuestion() {
        return this.question;
    }
    
    public void setQuestion(AuthenticationQuestion question) {
        this.question = question;
    }
    
    @XMLProperty
    public String getAnswer() {
        return this.answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
