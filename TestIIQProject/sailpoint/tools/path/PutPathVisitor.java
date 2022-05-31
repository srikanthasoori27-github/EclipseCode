
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;

/**
 * The put path visitor.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PutPathVisitor extends AbstractPathVisitor
{
    /**
     * The previous key.
     */
    private String prevKey;

    /**
     * The new value to put.
     */
    private Object newValue;

    /**
     * Constructs a new instance of PutPathVisitor.
     *
     * @param source The source map.
     * @param newValue The value to put at the path.
     */
    public PutPathVisitor(Map<String, Object> source, Object newValue)
    {
        super(source);

        this.newValue = newValue;
    }

    /**
     * {@inheritDoc}
     */
    public void visit(Path path) throws GeneralException
    {
        if (isMap(source)) {
            Map<String, Object> map = sourceAsMap();

            map.put(prevKey, newValue);
        } else {
            throw new GeneralException("Unexpected type found in path. Expected Map.");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void visit(MapKeyPathExpression pathExpression) throws GeneralException
    {
        if (hasPrevKey()) {
            if (isMap(source)) {
                Map<String, Object> map = sourceAsMap();

                source = ensureMapAtKey(map, prevKey);
            } else if (isList(source)) {
                List<Map<String, Object>> list = sourceAsList();

                if (list.size() != 1) {
                    throw new GeneralException("Setting a map value at a non-unique path is not supported");
                }

                Object firstObj = list.get(0);
                if (!isMap(firstObj)) {
                    throw new GeneralException("Unexpected type found in path. Expected Map.");
                }

                Map<String, Object> map = (Map<String, Object>) firstObj;

                source = map;
            }
        }

        prevKey = pathExpression.getKey();
    }

    /**
     * {@inheritDoc}
     */
    public void visit(ListFilterPathExpression pathExpression) throws GeneralException
    {
        if (!pathExpression.isUnique()) {
            throw new GeneralException("Setting a map value at a non-unique path is not supported");
        }

        if (hasPrevKey() && isMap(source)) {
            Map<String, Object> prevMap = sourceAsMap();

            source = ensureMapAtKey(prevMap, prevKey);
        }

        Map<String, Object> map;

        if (isMap(source)) {
            map = sourceAsMap();
        } else if (isList(source)) {
            List<Map<String, Object>> prevList = sourceAsList();

            map = prevList.get(0);
        } else {
            throw new GeneralException("Unable to filter on primitive type in path");
        }

        source = getFilteredListForExpr(map, pathExpression);

        prevKey = pathExpression.getKey();
    }

    /**
     * Extract the list represented by the path expression in the map.
     *
     * @param map The map.
     * @param pathExpression The path expression.
     * @return The list.
     * @throws GeneralException
     */
    private List<Map<String, Object>> getFilteredListForExpr(Map<String, Object> map, ListFilterPathExpression pathExpression) throws GeneralException
    {
        List<Map<String, Object>> list = ensureListAtKey(map, pathExpression.getKey());
        List<Map<String, Object>> filteredList = new ArrayList<Map<String, Object>>();

        Map<String, Object> item = ensureMapInList(list, pathExpression);

        filteredList.add(item);

        return filteredList;
    }

    /**
     * Ensures that a map exists in the list with the key and value. If one does
     * not exist then it is created.
     *
     * @param list The list.
     * @param pathExpr The list filter path expression.
     * @return The map.
     */
    private Map<String, Object> ensureMapInList(List<Map<String, Object>> list, ListFilterPathExpression pathExpr)
    {
        Map<String, Object> map = null;
        if (!list.isEmpty()) {
            for (Map<String, Object> listMap : list) {
                if (matchListFilterExpr(listMap, pathExpr)) {
                    map = listMap;

                    break;
                }
            }
        }

        if (null == map) {
            // if default key then always use 'sysName'
            String key = pathExpr.isDefaultFilterProperty() ? SYS_NAME_KEY : pathExpr.getProperty();

            map = new HashMap<String, Object>();
            map.put(key, pathExpr.getQuery());

            list.add(map);
        }

        return map;
    }

    /**
     * Ensures that a list exists in the map at the key. If one is not found
     * then it is created.
     *
     * @param map The map.
     * @param key The key.
     * @return The list.
     * @throws GeneralException
     */
    private List<Map<String, Object>> ensureListAtKey(Map<String, Object> map, String key) throws GeneralException
    {
        Object valAtKey = map.get(key);
        if (null == valAtKey) {
            valAtKey = new ArrayList<Map<String, Object>>();
            map.put(key, valAtKey);
        }

        if (!isList(valAtKey)) {
            throw new GeneralException("Unexpected type found in path. Expected List.");
        }

        return (List<Map<String, Object>>) valAtKey;
    }

    /**
     * Ensures that a map exists in the specified map at the key. If one is not found
     * then it is created.
     *
     * @param map The map.
     * @param key The key.
     * @return The map.
     * @throws GeneralException
     */
    private Map<String, Object> ensureMapAtKey(Map<String, Object> map, String key) throws GeneralException
    {
        Object valAtKey = map.get(key);
        if (null == valAtKey) {
            valAtKey = new HashMap<String, Object>();
            map.put(prevKey, valAtKey);
        }

        if (!isMap(valAtKey)) {
            throw new GeneralException("Unexpected type found in path. Expected Map.");
        }

        return (Map<String, Object>) valAtKey;
    }

    /**
     * Determines if there is a previous key.
     *
     * @return True if a previous key, false otherwise.
     */
    private boolean hasPrevKey()
    {
        return null != prevKey;
    }
}
