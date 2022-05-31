
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import java.util.List;
import java.util.Map;

/**
 * The base class for a path visitor.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
abstract class AbstractPathVisitor implements PathVisitor
{
    /**
     * The sysName key.
     */
    protected static final String SYS_NAME_KEY = "sysName";

    /**
     * The name key.
     */
    protected static final String NAME_KEY = "name";

    /**
     * The current source.
     */
    protected Object source;

    /**
     * Constructs a new instance of AbstractPathVisitor.
     *
     * @param source The source object.
     */
    protected AbstractPathVisitor(Object source)
    {
        this.source = source;
    }

    /**
     * Determines if the object is a Map.
     *
     * @param obj The object.
     * @return True if a Map, false otherwise.
     */
    protected boolean isMap(Object obj)
    {
        if (null == obj) {
            return false;
        }

        return obj instanceof Map;
    }

    /**
     * Determines if the object is a List.
     *
     * @param obj The object.
     * @return True if a List, false otherwise.
     */
    protected boolean isList(Object obj)
    {
        if (null == obj) {
            return false;
        }

        return obj instanceof List;
    }

    /**
     * Determines if the source property is null.
     *
     * @return True if the source is non null, false otherwise.
     */
    protected boolean hasSource()
    {
        return null != source;
    }

    /**
     * Gets the source property as a Map.
     *
     * @return The map.
     */
    protected Map<String, Object> sourceAsMap()
    {
        return (Map<String, Object>) source;
    }

    /**
     * Gets the source property as a List.
     *
     * @return The source.
     */
    protected List<Map<String, Object>> sourceAsList()
    {
        return (List<Map<String, Object>>) source;
    }

    /**
     * Determines if the value in the map at the specified key is equal to the object.
     *
     * @param map The map.
     * @param key The key.
     * @param obj The object.
     * @return True if equals, false otherwise.
     */
    protected boolean valueAtKeyEquals(Map<String, Object> map, String key, Object obj)
    {
        if (null == map || null == obj) {
            return false;
        }

        return obj.equals(map.get(key));
    }

    /**
     * Checks the filter expression against the map.
     *
     * @param map The map.
     * @param pathExpr The list filter path expression.
     * @return True if a match, false otherwise.
     */
    protected boolean matchListFilterExpr(Map<String, Object> map, ListFilterPathExpression pathExpr)
    {
        String query = pathExpr.getQuery();

        // if default key then first look for 'sysName' then 'name'
        if (pathExpr.isDefaultFilterProperty()) {
            return valueAtKeyEquals(map, SYS_NAME_KEY, query) || valueAtKeyEquals(map, NAME_KEY, query);
        }

        return valueAtKeyEquals(map, pathExpr.getProperty(), query);
    }
}
