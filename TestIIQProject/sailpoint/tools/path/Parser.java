
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * Parses the tokens of a path for the following grammar:
 *
 *  P -> PE
 *     | PE . P
 *
 * PE -> IE
 *     | IE [ IE ]
 *     | IE [ IE *= IE ]
 *     | IE [ IE = IE ]
 *     
 * PE -> IE
 *     | IE { IE }
 *     | IE { IE *= IE }
 *     | IE { IE = IE }
 *
 * IE -> i
 *     | s
 *
 * P  = path non-terminal
 * PE = path expression non-terminal
 * IE = identifier expression non-terminal
 * i  = identifier terminal
 * s  = string terminal
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class Parser
{
    /**
     * The tokens to parse.
     */
    private List<Token> tokens;

    /**
     * The current position in the token list.
     */
    private int currentPosition = -1;

    /**
     * Message representing a parse error.
     */
    private String errorMessage;

    /**
     * The AST stack.
     */
    private Stack<Object> astStack = new Stack<Object>();

    /**
     * Creates a new instance of PathParser.
     *
     * @param tokens The tokens.
     */
    public Parser(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    /**
     * Parses the path.
     *
     * @return True if the path syntax is valid, false otherwise.
     */
    public boolean parse()
    {
        boolean accept = match(new PathNonTerminal());
        if (!accept) {
            setErrorMessage();
        }

        return accept;
    }

    /**
     * Gets the current parser state.
     *
     * @return The current state.
     */
    public ParserState getCurrentState()
    {
        return new ParserState(currentPosition, astStack.size());
    }

    /**
     * Sets the state of the parser.
     *
     * @param state The new state.
     */
    public void setState(ParserState state)
    {
        currentPosition = state.getPosition();

        while (astStack.size() > state.getStackCount()) {
            astStack.pop();
        }
    }

    /**
     * Pushes an object onto the AST stack.
     *
     * @param obj The object.
     */
    public void push(Object obj)
    {
        astStack.push(obj);
    }

    /**
     * Pops and returns expressions off of the AST stack.
     *
     * @param numToPop The number to pop.
     * @return The match list.
     */
    public MatchList pop(int numToPop)
    {
        List<Object> matches = new ArrayList<Object>();
        for (int i = 0; i < numToPop; ++i) {
            matches.add(astStack.pop());
        }

        Collections.reverse(matches);

        return new MatchList(matches);
    }

    /**
     * Gets the current size of the AST stack.
     *
     * @return The size.
     */
    public int getStackSize()
    {
        return astStack.size();
    }

    /**
     * Matches a path token type.
     *
     * @param type The token type.
     * @return True if current token matches, false otherwise.
     */
    public boolean match(Token.Type type)
    {
        Token token = peekNextToken();
        if (null == token) {
            return false;
        }

        if(type.equals(token.getType())) {
            currentPosition++;

            astStack.push(token);

            return true;
        }

        return false;
    }

    /**
     * Matches a non terminal.
     *
     * @param nonTerminal The non terminal.
     * @return True if the non terminal matches the token stream input, false otherwise.
     */
    public boolean match(NonTerminal nonTerminal)
    {
        return nonTerminal.matches(this);
    }

    /**
     * Gets a parsed path.
     *
     * @return The parsed path.
     */
    public Path getParsedPath()
    {
        return (Path) astStack.peek();
    }

    /**
     * Gets the parsing error message if an error occurred.
     *
     * @return The message.
     */
    public String getErrorMessage()
    {
        return errorMessage;
    }

    /**
     * Peeks at the next token without consuming it.
     *
     * @return The token.
     */
    private Token peekNextToken()
    {
        if (currentPosition + 1 >= tokens.size()) {
            return null;
        }

        return tokens.get(currentPosition + 1);
    }

    /**
     * Sets the error message.
     */
    private void setErrorMessage()
    {
        Token token = peekNextToken();
        if (null == token) {
            errorMessage = "Encountered unexpected end of path";
        } else {
            errorMessage = String.format("Encountered unexpected token '%s' in path", token.getValue());
        }
    }

    /**
     * Represents the list of matched values for the current expression.
     *
     * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
     */
    public static class MatchList
    {
        /**
         * The matched values.
         */
        private List<Object> matches;

        /**
         * Constructs a new instance of MatchList.
         *
         * @param matches The matched values.
         */
        public MatchList(List<Object> matches)
        {
            this.matches = matches;
        }

        /**
         * Gets the value at the specified index.
         *
         * @param index The index.
         * @param <T> The value type.
         * @return The value.
         */
        public <T> T get(int index)
        {
            return (T) matches.get(index);
        }
    }
}
