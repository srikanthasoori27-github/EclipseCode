
/**
 * Package decl.
 */
package sailpoint.tools;

/**
 * Imports.
 */
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import sailpoint.tools.path.GetPathVisitor;
import sailpoint.tools.path.Lexer;
import sailpoint.tools.path.Parser;
import sailpoint.tools.path.Path;
import sailpoint.tools.path.PutAllPathVisitor;
import sailpoint.tools.path.PutPathVisitor;
import sailpoint.tools.path.Token;

/**
 * Utility class for retrieving a value from a map
 * specified by a path.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class MapUtil
{
    /**
     * Gets the value from a map at the specified path.
     * 
     * First it will check to see whether a key with .(dot) in name is present.
     * If not it will parse the path. So if we have a key "a.b.c"
     * then the value should be returned Otherwise the path a dot b dot c will be parsed.
     *
     * @param source The source map.
     * @param path The path.
     * @return The value at the path.
     * @throws GeneralException
     */
    public static Object get(Map<String, Object> source, String path) throws GeneralException
    {
        if (source == null || Util.isNullOrEmpty(path)) {
            return null;
        }

        if (source.containsKey(path)) {
            return source.get(path);
        }
        
        return getFromParsedPath(source, path);
    }

    private static Object getFromParsedPath(Map<String, Object> source, String path)
            throws GeneralException {

        Path parsedPath = lexAndParse(path);

        GetPathVisitor visitor = new GetPathVisitor(source);

        parsedPath.accept(visitor);

        return visitor.getValue();
    }

    /**
     * Sets the value in the map at the specified path to the new value.
     *
     * @param source The source map.
     * @param path The path.
     * @param newValue The new value.
     * @throws GeneralException
     */
    public static void put(Map<String, Object> source, String path, Object newValue) throws GeneralException
    {
        if (Util.isNullOrEmpty(path)) {
            return;
        }

        Path parsedPath = lexAndParse(path);

        PutPathVisitor visitor = new PutPathVisitor(source, newValue);

        parsedPath.accept(visitor);
    }

    /**
     * Applies the specified Function at the specified path to the source Map. If a list is encountered in the path,
     * the Function is applied to all values in the list.
     * @param source The source map.
     * @param path The path.
     * @param changer Function to apply to the value at the specified path.
     * @throws GeneralException
     */
    public static void putAll(Map<String, Object> source, String path, Function<Object, Object> changer) throws GeneralException
    {
        if (Util.isNullOrEmpty(path)) {
            return;
        }

        Path parsedPath;
        try {
            parsedPath = lexAndParse(path);
        } catch (GeneralException e) {
            // try quoted path instead if path contains Tokens at beginning of path or other unexpected syntax
            parsedPath = lexAndParse("\"" + path + "\"");
        }

        PutAllPathVisitor visitor = new PutAllPathVisitor(source, changer);

        parsedPath.accept(visitor);
    }

    /**
     * Lexes and parses the specified path.
     *
     * @param path The path.
     * @return The parsed path.
     * @throws GeneralException
     */
    private static Path lexAndParse(String path) throws GeneralException
    {
        List<Token> tokens = new Lexer(path).lex();

        Parser parser = new Parser(tokens);
        if (!parser.parse()) {
            throw new GeneralException(parser.getErrorMessage());
        }

        return parser.getParsedPath();
    }
}
