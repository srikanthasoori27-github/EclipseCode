package sailpoint.server;

/**
 * Exception thrown by CsrfService when token validation fails.
 *
 * @author jeff.upton
 */
public class CsrfValidationException 
    extends RuntimeException
{
    private static final String CSRF_VALIDATION_FAILED = "CSRF validation failed";

    public CsrfValidationException()
    {
        super(CSRF_VALIDATION_FAILED);
    }
}