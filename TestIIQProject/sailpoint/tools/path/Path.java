
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.GeneralException;

/**
 * The parsed path.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class Path implements PathExpression
{
    /**
     * The parsed path expressions.
     */
    List<PathExpression> pathExpressions = new ArrayList<PathExpression>();

    /**
     * Adds a path expression to the path.
     *
     * @param pathExpression The path expression.
     */
    public void addPathExpression(PathExpression pathExpression)
    {
        pathExpressions.add(pathExpression);
    }

    /**
     * Appends a path to this path.
     *
     * @param path The path.
     */
    public void addPath(Path path)
    {
        pathExpressions.addAll(path.pathExpressions);
    }

    /**
     * {@inheritDoc}
     */
    public void accept(PathVisitor visitor) throws GeneralException
    {
        for (PathExpression pathExpression : pathExpressions) {
            pathExpression.accept(visitor);
        }

        visitor.visit(this);
    }
}
