/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * Exception thrown when an action occurs that would cause a user to certify
 * themselves.
 */
public class SelfCertificationException extends GeneralException {

    private static final long serialVersionUID = 9127335827894810193L;

    private Identity selfCertifier;


    /**
     * Constructor.
     * 
     * @param  selfCertifier  The person attempting to certify themselves.
     */
    public SelfCertificationException(Identity selfCertifier) {
        super(new Message(MessageKeys.ERR_CANNOT_SELF_CERTIFY,
                                   selfCertifier.getDisplayableName()));
        this.selfCertifier = selfCertifier;
    }

    /**
     * Return the person attempting to certify themselves.
     */
    public Identity getSelfCertifier() {
        return this.selfCertifier;
    }
}
