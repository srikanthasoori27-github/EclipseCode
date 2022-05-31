
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * The identifier expression non terminal.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
class IdExprNonTerminal extends NonTerminal
{
    /**
     * Constructs a new instance of IdExprNonTerminal.
     */
    public IdExprNonTerminal()
    {
        // identifier expression production for: i
        addProduction(new TerminalProduction(Token.Type.IDENTIFIER) {
            public Object getValue(Parser.MatchList matchList) {
                Token token = matchList.get(0);

                return new IdExprValue(token.getValue());
            }
        });

        // identifier expression production for: s
        addProduction(new TerminalProduction(Token.Type.STRING) {
            public Object getValue(Parser.MatchList matchList) {
                Token token = matchList.get(0);

                return new IdExprValue(token.getValue());
            }
        });
    }
}
