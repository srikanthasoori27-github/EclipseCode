
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */

/**
 * The non terminal production abstract class.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
abstract class NonTerminalProduction implements Production
{
    /**
     * The NonTerminal to match.
     */
    private NonTerminal nonTerminal;

    /**
     * Constructs a new instance of NonTerminalProduction.
     *
     * @param nonTerminal The NonTerminal to match.
     */
    public NonTerminalProduction(NonTerminal nonTerminal)
    {
        this.nonTerminal = nonTerminal;
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(Parser parser)
    {
        return parser.match(nonTerminal);
    }
}
