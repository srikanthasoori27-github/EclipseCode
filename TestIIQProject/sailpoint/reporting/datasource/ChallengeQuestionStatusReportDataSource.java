/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.web.identity.IdentityProxy;

public class ChallengeQuestionStatusReportDataSource extends IdentityDataSource {
	
    private Iterator<Object []> ids;
    private Identity identity;
    private int numberOfQuestionsRequired = 0;
	/* JasperReport Field Names */
	private static final String LAST_LOGIN_DATE_FIELD_NAME = "lastLogin";
	private static final String NUM_ANSWERED_FIELD_NAME = "numAnswered";
    private static final Log log = LogFactory.getLog(UserForwardingDataSource.class);
	
	public ChallengeQuestionStatusReportDataSource( List<Filter> filters,
													Locale locale, TimeZone timezone,
													Attributes<String, Object> inputs ) {
		super( filters, locale, timezone, inputs );
	}
	
    @Override
    public void internalPrepare() throws GeneralException {
        try {
            List<String> props = new ArrayList<String>();
            props.add("id");
            
            ids = getContext().search(Identity.class, qo, props);
            numberOfQuestionsRequired = getNumberOfRequiredQuestions();
        } catch (GeneralException ge) {
            log.error("GeneralException caught while executing search for identities: " + ge.getMessage());
        } finally {
            /* If somehow id == null dummy up a value */
            if( ids == null ) {
                ids = new Iterator<Object[]>() {
                    public void remove() {
                    }
                    public Object[] next() {
                        throw new RuntimeException( "" ); 
                    }
                    public boolean hasNext() {
                        return false;
                    }
                };
            }
        }
    }
	
	@Override
	public Object getFieldValue(JRField jrField) throws JRException {
		Object response = null;
		String fieldName = jrField.getName();
		/* Check two report specific fields before passing off to IdentityDataSource */
		if( LAST_LOGIN_DATE_FIELD_NAME.equals( fieldName ) ) {
			if( identity.getLastLogin() != null ) {
				response = Internationalizer.getLocalizedDate( identity.getLastLogin(), getLocale(), getTimezone() );
			}
		}
		if( NUM_ANSWERED_FIELD_NAME.equals( fieldName ) ) {
        	int numberOfQuestions = 0;
        	if( identity.getAuthenticationAnswers() != null ) {
	        	for (AuthenticationAnswer answer: identity.getAuthenticationAnswers() ) {
					if( answer.getQuestion() != null ) {
						numberOfQuestions++;
					}
				}
        	}
            response = Integer.toString( numberOfQuestions ) + "/" + Integer.toString( numberOfQuestionsRequired );
		} else {
            response = IdentityProxy.get( identity, fieldName );
		}
		return response;
	}
	
    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    public boolean internalNext() throws JRException {
        boolean identityIsInteresting = false;
        /* Decache the identity we processed previously */
        decacheIdentity( identity );
        Identity tmpIdentity = null;
        /* Iterate over Identitys looking for the next "interesting" Identity */
        boolean hasNext = ids.hasNext();
        while( hasNext && !identityIsInteresting ) {
            /* Decache that uninteresting loser */
            decacheIdentity( tmpIdentity );
            
            try {
                /* Fetch the user and determine if he is interesting */
                tmpIdentity = getContext().getObjectById(Identity.class, (String)(ids.next()[0]));
                if( identityIsInteresting( tmpIdentity ) ) {
                    identity = tmpIdentity;
                    identityIsInteresting = true;
                    updateProgress("Identity", identity.getName());
                } else {
                    hasNext = ids.hasNext();
                }
            } catch (GeneralException ge) {
                log.error("GeneralException caught while trying to load identity. " + ge.getMessage());
            }
        }
        return hasNext;
    }
    
	private boolean identityIsInteresting( Identity identity ) {
        List<AuthenticationAnswer> authenticationAnswers = identity.getAuthenticationAnswers();
        int answerCount = 0;
	    if( authenticationAnswers != null ) {
	        for ( AuthenticationAnswer authenticationAnswer : authenticationAnswers ) {
                if( authenticationAnswer.getQuestion() != null && authenticationAnswer != null ) {
                    answerCount++;
                }
            }
	    }
        return answerCount < numberOfQuestionsRequired;
    }

    private void decacheIdentity( Identity identity ) {
        try {
            if ( identity != null ) {
                getContext().decache( identity );
            }
        } catch (Exception e) {
            log.warn("Unable to decache identity." + e.toString());
        }
    }

    private int getNumberOfRequiredQuestions() throws GeneralException {
		return getContext().getConfiguration().getInt( Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED );
	}
}
