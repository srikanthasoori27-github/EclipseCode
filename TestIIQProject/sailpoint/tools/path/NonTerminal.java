
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.List;

/**
 * The non terminal class.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
abstract class NonTerminal
{
    /**
     * The productions which this non terminal matches.
     */
    private List<Production> productions = new ArrayList<Production>();

    /**
     * Adds a production to match.
     *
     * @param p The production.
     */
    protected void addProduction(Production p)
    {
        productions.add(p);
    }

    /**
     * Tests to see if any of the non terminals productions matches.
     *
     * @return True if a production matches.
     */
    public boolean matches(Parser parser)
    {
        ParserState savedState = parser.getCurrentState();

        for (Production production : productions) {
            parser.setState(savedState);

            if (production.matches(parser)) {
                int numMatchedSymbols = parser.getStackSize() - savedState.getStackCount();
                Parser.MatchList matches = parser.pop(numMatchedSymbols);

                parser.push(production.getValue(matches));

                return true;
            }

        }

        return false;
    }
}
