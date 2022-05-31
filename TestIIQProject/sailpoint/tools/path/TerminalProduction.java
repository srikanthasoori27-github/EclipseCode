
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * The terminal production abstract class.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
abstract class TerminalProduction implements Production
{
    /**
     * The token type that this terminal production matches.
     */
    private Token.Type tokenType;

    /**
     * Constructs a new instance of TerminalProduction.
     *
     * @param tokenType The token type to match.
     */
    public TerminalProduction(Token.Type tokenType)
    {
        this.tokenType = tokenType;
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(Parser parser)
    {
        return parser.match(tokenType);
    }
}
