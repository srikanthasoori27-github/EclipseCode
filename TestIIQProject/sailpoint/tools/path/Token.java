
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Represents a token in the path.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class Token
{
    /**
     * Enumeration of the token types.
     *
     * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
     */
    public enum Type
    {
        EQUALS,
        ASTERISK,
        DOT,
        OPEN_BRACKET,
        CLOSE_BRACKET,
        IDENTIFIER,
        STRING,
        EOF,
        OPEN_BRACE,
        CLOSE_BRACE;
    }

    /**
     * The token type.
     */
    private Type tokenType;

    /**
     * The token value.
     */
    private String value;

    /**
     * Constructs a new instance of PathToken.
     *
     * @param tokenType The token type.
     */
    public Token(Type tokenType)
    {
        this(tokenType, "");
    }

    /**
     * Constructs a new instance of PathToken.
     *
     * @param tokenType The token type.
     * @param value The value.
     */
    public Token(Type tokenType, String value)
    {
        this.tokenType = tokenType;
        this.value = value;
    }

    /**
     * Gets the type of the token.
     *
     * @return The token type.
     */
    public Type getType()
    {
        return tokenType;
    }

    /**
     * Gets the value of the token.
     *
     * @return The value.
     */
    public String getValue()
    {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return String.format("%s [%s]", tokenType.name(), value);
    }
}
