
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexes a path into a collection of path tokens.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class Lexer
{
    /**
     * The end of file char.
     */
    private static final char EOF = '\0';

    /**
     * The source string to lex.
     */
    private String source;

    /**
     * The current position of the lexer in the source.
     */
    private int currentPosition;

    /**
     * Constructs a new instance of PathLexer.
     *
     * @param source The source to lex.
     */
    public Lexer(String source)
    {
        this.source = source;
        this.currentPosition = -1;
    }

    /**
     * Lex the source and create the path tokens.
     *
     * @return The tokens.
     */
    public List<Token> lex() throws GeneralException
    {
        List<Token> tokens = new ArrayList<Token>();

        Token token;
        for (token = nextToken(); !isEofToken(token); token = nextToken()) {
            tokens.add(token);
        }

        tokens.add(new Token(Token.Type.EOF));

        return tokens;
    }

    /**
     * Determines if the token is of type EOF.
     *
     * @param token The token.
     * @return True if the token is EOF, false otherwise.
     */
    private boolean isEofToken(Token token)
    {
        if (null == token) {
            return false;
        }

        return Token.Type.EOF.equals(token.getType()) ;
    }

    /**
     * Creates a token.
     *
     * @param type The token type.
     * @return The path token.
     */
    private Token makeToken(Token.Type type)
    {
        return makeToken(type, "");
    }

    /**
     * Creates a token.
     *
     * @param type The token type.
     * @param value The value.
     * @return The path token.
     */
    private Token makeToken(Token.Type type, String value)
    {
        return new Token(type, value);
    }

    /**
     * Gets the next token from the source.
     *
     * @return The token.
     */
    private Token nextToken() throws GeneralException
    {
        readWhitespace();

        char c = nextChar();

        if (c == EOF) {
            return makeToken(Token.Type.EOF);
        }

        if (c == '.') {
            return makeToken(Token.Type.DOT);
        }

        if (isIdentifierStartChar(c)) {
            return readIdentifier(c);
        }

        if (c == '*') {
            return makeToken(Token.Type.ASTERISK);
        }

        if (c == '=') {
            return makeToken(Token.Type.EQUALS);
        }

        if (c == '[') {
            return makeToken(Token.Type.OPEN_BRACKET);
        }

        if (c == ']') {
            return makeToken(Token.Type.CLOSE_BRACKET);
        }

        if (c == '"' || c == '\'') {
            return readString(c);
        }
        
        if (c == '{') {
            return makeToken(Token.Type.OPEN_BRACE);
        }
        
        if (c== '}') {
            return makeToken(Token.Type.CLOSE_BRACE);
        }

        throw new GeneralException(String.format("Unexpected character '%s' encountered at position %d of path '%s'", c, currentPosition, source));
    }

    /**
     * Determines if the character is a valid start character
     * for an identifier.
     *
     * @param c The char.
     * @return True if a valid start char, false otherwise.
     */
    private boolean isIdentifierStartChar(char c)
    {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '@';
    }

    /**
     * Determines if the character is a valid identifier character.
     *
     * @param c The char.
     * @return True if a valid char, false otherwise.
     */
    private boolean isIdentifierChar(char c)
    {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-' || c == '@';
    }

    /**
     * Gets the next char from the source.
     *
     * @return The char.
     */
    private char nextChar()
    {
        ++currentPosition;

        if (currentPosition >= source.length()) {
            return EOF;
        }

        return source.charAt(currentPosition);
    }

    /**
     * Moves the current character position in the source back one.
     */
    private void moveBack()
    {
        --currentPosition;
    }

    /**
     * Creates a identifier token from the source starting with the
     * specified character.
     *
     * @param c The character.
     * @return The token.
     */
    private Token readIdentifier(char c)
    {
        StringBuffer identifierBuffer = new StringBuffer();

        do {
            identifierBuffer.append(c);

            c = nextChar();
        } while (isIdentifierChar(c));

        if (c == '\\') {
            char d = nextChar();

            if (d == '.') {

                do {
                    identifierBuffer.append(d);

                    d = nextChar();
                } while (isIdentifierChar(d));
            }
        }

        moveBack();

        return makeToken(Token.Type.IDENTIFIER, identifierBuffer.toString());
    }

    /**
     * Creates a string token from the source.
     *
     * @param startChar The char the string started with.
     * @return The token.
     */
    private Token readString(char startChar)
    {
        StringBuffer stringBuffer = new StringBuffer();

        char c = nextChar();

        while (c != startChar) {
            if (c == '\\') {
                char d = nextChar();

                if (d == c) {
                    stringBuffer.append(d);
                }
            } else {
                stringBuffer.append(c);
            }

            c = nextChar();
        }

        return makeToken(Token.Type.STRING, stringBuffer.toString());
    }

    /**
     * Reads whitespace from the current position until a non-whitespace
     * character is encountered.
     */
    private void readWhitespace()
    {
        char c;

        do {
            c = nextChar();
        } while (Character.isWhitespace(c));

        moveBack();
    }
}

