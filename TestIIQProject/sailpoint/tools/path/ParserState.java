
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Represents the state of the parser.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
class ParserState
{
    /**
     * The position in the token list.
     */
    private int position;

    /**
     * The count of values in the stack.
     */
    private int stackCount;

    /**
     * Constructs a new instance of ParserState.
     *
     * @param position The position.
     * @param stackCount The stack count.
     */
    public ParserState(int position, int stackCount)
    {
        this.position = position;
        this.stackCount = stackCount;
    }

    /**
     * Gets the position.
     *
     * @return The position.
     */
    public int getPosition()
    {
        return position;
    }

    /**
     * Gets the stack count.
     *
     * @return The stack count.
     */
    public int getStackCount()
    {
        return stackCount;
    }
}
