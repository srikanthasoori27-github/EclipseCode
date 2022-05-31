package sailpoint.service.classification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Utility class for getting classification data
 */
public class ClassificationService {
    private SailPointContext context;

    /**
     * Constructor
     * @param context
     */
    public ClassificationService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Get the classification names associated with the classifiable id.
     * @param clazz
     * @param classifiableId
     * @return List<String> list of classification names
     */
    public List<String> getClassificationNames(Class<? extends SailPointObject> clazz, String classifiableId) throws GeneralException {
        List<String> classifications = new ArrayList<>();

        if (clazz == null || Util.isNullOrEmpty(classifiableId)) {
            return classifications;
        }

        QueryOptions ops = new QueryOptions(Filter.eq("classifications.ownerId", classifiableId));
        ops.add(Filter.eq("classifications.ownerType", clazz.getSimpleName()));
        ops.addOrdering("classifications.classification.displayableName", true);
        ops.setDistinct(true);
        Iterator<Object[]> names = this.context.search(clazz, ops, "classifications.classification.displayableName");

        if (names != null) {
            while (names.hasNext()) {
                String displayName = (String) names.next()[0];
                if (Util.isNotNullOrEmpty(displayName)) {
                    classifications.add(displayName);
                }
            }
        }
        return classifications;
    }

    /**
     * Get the displayable names for classifications given a list of names. If some of the Classifications
     * no longer exist, include their names in the list as-is.
     * @param classificationNames List of Classification names.
     * @return Sorted list of displayable names, including names for deleted Classifications.
     * @throws GeneralException
     */
    public List<String> getDisplayableNames(List<String> classificationNames) throws GeneralException {
        List<String> displayNames = null;
        if (Util.size(classificationNames) > 0) {

            List<String> originalNames = new ArrayList<>(classificationNames);
            QueryOptions ops = new QueryOptions(Filter.in("name", classificationNames));
            final List<String> queryProperties = Arrays.asList("name", "displayableName");

            Iterator<Object[]> it = this.context.search(Classification.class, ops, queryProperties);
            displayNames = new ArrayList<>();

            while (it.hasNext()) {
                Object[] result = it.next();
                String name = (String) result[0];
                String displayableName = (String) result[1];

                // If we removed it from our copied list, then we know that the Classification object
                // still exists in the DB.
                if (originalNames.remove(name)) {
                    displayNames.add(displayableName);
                }
                // If nothing got removed then we know it is a stale classification on the cert item
                // that we still want to display for the popup, add it back at the end
            }

            // Add back the names that were not found in the DB
            displayNames.addAll(originalNames);

            Collections.sort(displayNames);
        }

        return displayNames;
    }


}
