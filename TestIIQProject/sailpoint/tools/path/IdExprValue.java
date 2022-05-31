
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Holds the value of an identifier expression.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
class IdExprValue
{
    /**
     * The value.
     */
    private String value;

    /**
     * Constructs a new instance of IdExprValue.
     *
     * @param value The value.
     */
    public IdExprValue(String value)
    {
        this.value = value;
    }

    /**
     * Gets the value that represents the identifier expression.
     *
     * @return The value.
     */
    public String getValue()
    {
        return value;
    }
}
