
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import sailpoint.tools.GeneralException;

/**
 * The list filter path expression.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
class ListFilterPathExpression implements PathExpression
{
    /**
     * The key used to access the list.
     */
    private String key;

    /**
     * The property to query on.
     */
    private String property;

    /**
     * The query.
     */
    private String query;

    /**
     * Whether or not to return a unique result.
     */
    private boolean unique;

    /**
     * Creates a new instance of ListFilterPathExpression with
     * the default property and uniqueness.
     *
     * @param key The key.
     * @param query The query.
     */
    public ListFilterPathExpression(String key, String query)
    {
        this(key, null, query, true);
    }

    /**
     * Creates a new instance of ListFilterPathExpression with uniqueness.
     *
     * @param key The key.
     * @param property The property.
     * @param query The filter.
     */
    public ListFilterPathExpression(String key, String property, String query)
    {
        this(key, property, query, true);
    }

    /**
     * Creates a new instance of ListFilterPathExpression.
     *
     * @param key The key.
     * @param property The property.
     * @param query The filter.
     * @param unique Should return a unique result.
     */
    public ListFilterPathExpression(String key, String property, String query, boolean unique)
    {
        this.key = key;
        this.property = property;
        this.query = query;
        this.unique = unique;
    }

    /**
     * {@inheritDoc}
     */
    public void accept(PathVisitor visitor) throws GeneralException
    {
        visitor.visit(this);
    }

    /**
     * Gets the key.
     *
     * @return The key.
     */
    public String getKey()
    {
        return key;
    }

    /**
     * Gets the property.
     *
     * @return The property.
     */
    public String getProperty()
    {
        return property;
    }

    /**
     * Gets the query.
     *
     * @return The query.
     */
    public String getQuery()
    {
        return query;
    }

    /**
     * Determines if the result of the expression should be unique or a list.
     *
     * @return True for unique, false otherwise.
     */
    public boolean isUnique()
    {
        return unique;
    }

    /**
     * Determines if a filter property was specified or not.
     *
     * @return True if no property was specified, false otherwise.
     */
    public boolean isDefaultFilterProperty()
    {
        return null == property;
    }
}
