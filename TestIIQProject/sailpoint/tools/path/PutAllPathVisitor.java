/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools.path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import sailpoint.tools.GeneralException;

/**
 * Similar to {@link sailpoint.tools.path.GetPathVisitor} you may specify a path that contains
 * multiple results. If the source Map contains one or more matches the function will be applied
 * to the value and replace the existing value in the Map.
 * @ignore
 * Similar to AbstractPathVisitor, each time a PathExpression is visited, sourceList changes and
 * iterates further down the Path. When a Map is encountered, the map is added to sourceList. When
 * a List is encountered, all objects are added to the sourceList. Further path expressions act on
 * all members of the list, rather than a single Map as they do in AbstractPathVisitor.
 */
public class PutAllPathVisitor implements PathVisitor
{
    private List<Map<String, Object>> sourceList = new ArrayList<>();
    private String prevKey;
    private Function<Object, Object> changer;

    /**
     * Constructs a new instance of PutAllPathVisitor.
     *
     * @param source The source map.
     * @param changer A function to apply to the value found in source
     */
    public PutAllPathVisitor(Map<String, Object> source, Function<Object, Object> changer)
    {
        this.sourceList.add(source);
        this.changer = changer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visit(Path path) throws GeneralException {
        for (Map<String, Object> map : sourceList) {
            Object newValue = changer.apply(map.get(prevKey));
            if (newValue != null) map.put(prevKey, newValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visit(MapKeyPathExpression pathExpression) throws GeneralException {
        if (hasPrevKey()) {

            List<Map<String, Object>> newList = new ArrayList<>();
            for (Map<String, Object> map : sourceList) {
                Object obj = map.get(prevKey);
                if (obj instanceof List) {
                    newList.addAll((List)obj);
                }
                
                if (obj instanceof Map) {
                    newList.add((Map)obj);
                }
            }
            
            if (!newList.isEmpty())
                sourceList = newList;
        }

        prevKey = pathExpression.getKey();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void visit(ListFilterPathExpression pathExpression) throws GeneralException {
        throw new UnsupportedOperationException("Filtering a list is not supported in the PutAllPathVisitor");
    }

    /**
     * Determines if there is a previous key.
     *
     * @return True if a previous key, false otherwise.
     */
    private boolean hasPrevKey()  {
        return null != prevKey;
    }
}