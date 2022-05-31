
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import sailpoint.tools.GeneralException;

/**
 * The path expression interface.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
interface PathExpression
{
    /**
     * Accepts a path visitor.
     *
     * @param visitor The visitor.
     * @throws GeneralException
     */
    void accept(PathVisitor visitor) throws GeneralException;
}
