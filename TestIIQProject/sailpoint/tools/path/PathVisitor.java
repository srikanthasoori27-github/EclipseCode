
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import sailpoint.tools.GeneralException;

/**
 * The path visitor interface.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
interface PathVisitor
{
    /**
     * Visits a Path object.
     *
     * @param path The path.
     * @throws GeneralException
     */
    void visit(Path path) throws GeneralException;

    /**
     * Visits a map key path expression.
     *
     * @param pathExpression The path expression.
     * @throws GeneralException
     */
    void visit(MapKeyPathExpression pathExpression) throws GeneralException;

    /**
     * Visits a list filter path expression.
     *
     * @param pathExpression The path expression.
     * @throws GeneralException
     */
    void visit(ListFilterPathExpression pathExpression) throws GeneralException;
}
