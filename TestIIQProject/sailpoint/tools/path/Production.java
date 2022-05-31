
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * The production interface.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
interface Production
{
    /**
     * Determines if the productions matches the token input.
     *
     * @return True if it matches, false otherwise.
     */
    boolean matches(Parser parser);

    /**
     * Gets the value that the production represents.
     *
     * @param matchList The list of matched values.
     * @return The value.
     */
    Object getValue(Parser.MatchList matchList);
}
