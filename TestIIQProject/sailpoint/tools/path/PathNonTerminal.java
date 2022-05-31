/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools.path;

/**
 * The path non terminal class.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
class PathNonTerminal extends NonTerminal
{
    /**
     * Constructs a new instance of PathNonTerminal
     */
    public PathNonTerminal()
    {
        // path production for: PE
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new PathExprNonTerminal()) && parser.match(Token.Type.EOF);
            }

            public Object getValue(Parser.MatchList matchList) {
                PathExpression expr = matchList.get(0);

                Path path = new Path();
                path.addPathExpression(expr);

                return path;
            }
        });

        // path production for: PE . P
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new PathExprNonTerminal()) && parser.match(Token.Type.DOT) &&
                       parser.match(new PathNonTerminal());
            }

            public Object getValue(Parser.MatchList matchList) {
                PathExpression expr = matchList.get(0);
                Path otherPath = matchList.get(2);

                Path path = new Path();
                path.addPathExpression(expr);
                path.addPath(otherPath);

                return path;
            }
        });
    }
}
