
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * The path expression non terminal.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
class PathExprNonTerminal extends NonTerminal
{
    /**
     * Constructs a new instance of PathExprNonTerminal.
     */
    public PathExprNonTerminal()
    {
        // path expression production for: IE [ IE *= IE ]
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.OPEN_BRACKET) &&
                        parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.ASTERISK) &&
                        parser.match(Token.Type.EQUALS) && parser.match(new IdExprNonTerminal()) &&
                        parser.match(Token.Type.CLOSE_BRACKET);
            }

            public Object getValue(Parser.MatchList matchList) {
                IdExprValue key = matchList.get(0);
                IdExprValue property = matchList.get(2);
                IdExprValue query = matchList.get(5);

                return new ListFilterPathExpression(key.getValue(), property.getValue(), query.getValue(), false);
            }
        });
        
        // path expression production for: IE { IE *= IE }
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.OPEN_BRACE) &&
                        parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.ASTERISK) &&
                        parser.match(Token.Type.EQUALS) && parser.match(new IdExprNonTerminal()) &&
                        parser.match(Token.Type.CLOSE_BRACE);
            }

            public Object getValue(Parser.MatchList matchList) {
                IdExprValue key = matchList.get(0);
                IdExprValue property = matchList.get(2);
                IdExprValue query = matchList.get(5);

                return new ListFilterPathExpression(key.getValue(), property.getValue(), query.getValue(), false);
            }
        });

        // path expression production for: IE [ IE = IE ]
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.OPEN_BRACKET) &&
                       parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.EQUALS) &&
                       parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.CLOSE_BRACKET);
            }

            public Object getValue(Parser.MatchList matchList) {
                IdExprValue key = matchList.get(0);
                IdExprValue property = matchList.get(2);
                IdExprValue query = matchList.get(4);

                return new ListFilterPathExpression(key.getValue(), property.getValue(), query.getValue());
            }
        });
        
        // path expression production for: IE { IE = IE }
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.OPEN_BRACE) &&
                       parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.EQUALS) &&
                       parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.CLOSE_BRACE);
            }

            public Object getValue(Parser.MatchList matchList) {
                IdExprValue key = matchList.get(0);
                IdExprValue property = matchList.get(2);
                IdExprValue query = matchList.get(4);

                return new ListFilterPathExpression(key.getValue(), property.getValue(), query.getValue());
            }
        });

        // path expression production for: IE [ IE ]
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.OPEN_BRACKET) &&
                       parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.CLOSE_BRACKET);
            }

            public Object getValue(Parser.MatchList matchList) {
                IdExprValue key = matchList.get(0);
                IdExprValue query = matchList.get(2);

                return new ListFilterPathExpression(key.getValue(), query.getValue());
            }
        });
        
        // path expression production for: IE { IE }
        addProduction(new Production() {
            public boolean matches(Parser parser) {
                return parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.OPEN_BRACE) &&
                       parser.match(new IdExprNonTerminal()) && parser.match(Token.Type.CLOSE_BRACE);
            }

            public Object getValue(Parser.MatchList matchList) {
                IdExprValue key = matchList.get(0);
                IdExprValue query = matchList.get(2);

                return new ListFilterPathExpression(key.getValue(), query.getValue());
            }
        });

        // path expression production for: IE
        addProduction(new NonTerminalProduction(new IdExprNonTerminal()) {
            public Object getValue(Parser.MatchList matchList) {
                IdExprValue expr = matchList.get(0);

                return new MapKeyPathExpression(expr.getValue());
            }
        });
    }
}
