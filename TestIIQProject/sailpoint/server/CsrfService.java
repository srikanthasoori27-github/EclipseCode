package sailpoint.server;

import java.security.SecureRandom;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.Base64;
import sailpoint.tools.Util;

/**
 * Service class that encapsulates CSRF operations on the user's session.
 */
public class CsrfService 
{
    private static final Log LOG = LogFactory.getLog(CsrfService.class);

    /**
     * The name of the CSRF session attribute.
     */
    public static final String CSRF_TOKEN_ATTRIBUTE = "csrfToken";

    private HttpSession session;

    /**
     * Constructs a CsrfService that manipulates the session associated
     * with a FacesContext.
     *
     * @param context The FacesContext that holds the session to manipulate.
     */
    public CsrfService(FacesContext context)
    {
        this((HttpSession)context.getExternalContext().getSession(false));
    }

    /**
     * Constructs a CsrfService that manipulates an HttpSession.
     *
     * @param session The session to manipulate.
     */
    public CsrfService(HttpSession session) 
    {
        if (session == null) {
            throw new RuntimeException("Session cannot be null");
        }

        this.session = session;
    }

    /**
     * Gets the CSRF token associated with this session.
     */
    public String getToken() 
    {
        return (String)session.getAttribute(CSRF_TOKEN_ATTRIBUTE);
    }

    /**
     * Resets the CSRF token associated with this session.
     */
    public void resetToken()
    {
        session.setAttribute(CSRF_TOKEN_ATTRIBUTE, generateToken());
    }

    /**
     * Validates that the CSRF token associated with the current session
     * matches the specified token. An exception is thrown if validation fails.
     *
     * @param inputToken The token to test.
     */
    public void validate(String inputToken, String uri)
    {
        if (!Util.nullSafeEq(getToken(), inputToken)) {
            LOG.error("CSRF validation failed for " + uri);
            throw new CsrfValidationException();
        }
    }

    /**
     * Generates a 128-bit base64 encoded CSRF token
     */
    public static String generateToken()
    {
        byte[] bytes = new byte[32];
        
        SecureRandom random = new SecureRandom();
        random.nextBytes(bytes);
        
        
        return Base64.encodeBytes(bytes);
    }
}