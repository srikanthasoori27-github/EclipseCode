
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import sailpoint.tools.GeneralException;

/**
 * The map key path expression.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
class MapKeyPathExpression implements PathExpression
{
    /**
     * The map key.
     */
    private String key;

    /**
     * Constructs a new instance of IdentifierPathExpression.
     *
     * @param key The map key.
     */
    public MapKeyPathExpression(String key)
    {
        this.key = key;
    }

    /**
     * Gets the map key.
     *
     * @return The key.
     */
    public String getKey()
    {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    public void accept(PathVisitor visitor) throws GeneralException
    {
        visitor.visit(this);
    }
}
