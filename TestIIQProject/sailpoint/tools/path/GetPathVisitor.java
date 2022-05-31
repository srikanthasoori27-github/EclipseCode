
/**
 * Package decl.
 */
package sailpoint.tools.path;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;

/**
 * The get path visitor.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class GetPathVisitor extends AbstractPathVisitor
{
    /**
     * Constructs a new instance of GetPathVisitor.
     *
     * @param source The source map.
     */
    public GetPathVisitor(Map<String, Object> source)
    {
        super(source);
    }

    /**
     * {@inheritDoc}
     */
    public void visit(Path path) throws GeneralException
    {
        // at this point source field should contain the value
    }

    /**
     * {@inheritDoc}
     */
    public void visit(MapKeyPathExpression pathExpression) throws GeneralException
    {
        if (hasSource()) {
            if (isMap(source)) {
                Map<String,Object> map = sourceAsMap();

                source = map.get(pathExpression.getKey());
            } else if (isList(source)) {
                List<Map<String, Object>> list = sourceAsList();

                List<Object> valueList = new ArrayList<Object>();
                for (Map<String, Object> map : list) {
                    valueList.add(map.get(pathExpression.getKey()));
                }

                source = valueList;
            } else {
                throw new GeneralException("Unable to index into primitive type in path");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void visit(ListFilterPathExpression pathExpression) throws GeneralException
    {
        if (hasSource()) {
            if (isMap(source)) {
                Map<String, Object> map = sourceAsMap();

                source = extractListExprValueFromMap(map, pathExpression);
            } else if (isList(source)) {
                List<Map<String, Object>> mapList = sourceAsList();

                source = extractListExprValueFromList(mapList, pathExpression);
            } else {
                throw new GeneralException("Unable to filter on primitive type in path");
            }
        }
    }

    /**
     * Gets the value at the path.
     *
     * @return The value.
     */
    public Object getValue()
    {
        return source;
    }

    /**
     * Extracts the value represented by the list expression from the map.
     *
     * @param map The map.
     * @param pathExpr The list filter path expression.
     * @return The value.
     * @throws GeneralException
     */
    private Object extractListExprValueFromMap(Map<String, Object> map, ListFilterPathExpression pathExpr) throws GeneralException
    {
        if (map.containsKey(pathExpr.getKey())) {
            List<Map<String, Object>> list = checkListAtKey(map, pathExpr.getKey());

            List<Map<String, Object>> filteredList = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> listMap : list) {
                if (matchListFilterExpr(listMap, pathExpr)) {
                    filteredList.add(listMap);
                }
            }

            if (pathExpr.isUnique()) {
                return filteredList.isEmpty() ? null : filteredList.get(0);
            }

            return filteredList;
        }

        return null;
    }

    /**
     * Extracts the value represented by the list expression from the list.
     *
     * @param list The list.
     * @param pathExpr The list filter path expression.
     * @return The value.
     * @throws GeneralException
     */
    private Object extractListExprValueFromList(List<Map<String, Object>> list, ListFilterPathExpression pathExpr) throws GeneralException
    {
        List<Map<String, Object>> filteredList = new ArrayList<Map<String, Object>>();

        for (Map<String, Object> map : list) {
            if (map.containsKey(pathExpr.getKey())) {
                List<Map<String, Object>> subList = checkListAtKey(map, pathExpr.getKey());

                for (Map<String, Object> listMap : subList) {
                    if (matchListFilterExpr(listMap, pathExpr)) {
                        filteredList.add(listMap);
                    }
                }
            }
        }

        if (pathExpr.isUnique()) {
            return filteredList.isEmpty() ? null : filteredList.get(0);
        }

        return filteredList;
    }

    /**
     * Checks that the object at the key in the map is a List.
     *
     * @param map The map.
     * @param key The key.
     * @return The list.
     * @throws GeneralException
     */
    private List<Map<String, Object>> checkListAtKey(Map<String, Object> map, String key) throws GeneralException
    {
        Object objAtKey = map.get(key);
        if (!isList(objAtKey)) {
            throw new GeneralException("Unexpected type found in path. Expected List.");
        }

        return (List<Map<String, Object>>) objAtKey;
    }
}
