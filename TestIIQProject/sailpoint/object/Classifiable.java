/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import sailpoint.tools.Util;

/**
 * Interface for objects that support Classifications
 */
public interface Classifiable {

    /**
     * Get all the classifications for the object
     * @return List of Classificaitons
     */
    List<ObjectClassification> getClassifications();

    /**
     * Set the classifications on the object
     * @param classifications List of Classifications
     */
    void setClassifications(List<ObjectClassification> classifications);

    /**
     * Add a single Classification to the object. If the Classification
     * already exists, there should be no modification.
     * @param classification Classification object
     * @return True if the new Classification was added to the list, otherwise false.
     */
    boolean addClassification(ObjectClassification classification);

    /**
     * Remove a single Classification from the object. If the Classification
     * does not already exist, there should be no modification.
     * @param classification Classification object
     * @return True if the Classification was removed from the list, otherwise false.
     */
    boolean removeClassification(ObjectClassification classification);

    /**
     * Return a list of display names of the classifications for this object.
     * @return List of display names of each classification
     */
    default List<String> getClassificationDisplayNames() {
        if (!Util.isEmpty(this.getClassifications())) {
            return this.getClassifications().stream()
                                            .map(classification -> classification.getClassification().getDisplayableName())
                                            .distinct()
                                            .sorted()
                                            .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    default List<String> getClassificationNames() {
        if (!Util.isEmpty(this.getClassifications())) {
            return this.getClassifications().stream()
                    .map(classification -> classification.getClassification().getName())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    /**
     *
     * @param classification - Classification to associate
     * @param source - Source of the Classification Assignment
     * @param effective - True if this is an effective Classification, false if direct
     * @return
     */
    default boolean addClassification(Classification classification, String source, boolean effective) {
        boolean added = false;
        if (classification != null) {
            if (this.getClassifications() == null) {
                this.setClassifications(new ArrayList<>());
            }

            boolean found = false;
            for (ObjectClassification oc : Util.safeIterable(this.getClassifications())) {
                //Search for direct ObjectClassifications for the same classification
                if (effective == oc.isEffective() && Util.nullSafeEq(oc.getClassification().getName(), classification.getName())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                //Create new
                ObjectClassification objC = new ObjectClassification();
                objC.setSource(source);
                //This will get set by Hibernate cascade
                objC.setOwnerType(getClass().getSimpleName());
                objC.setClassification(classification);
                objC.setEffective(effective);
                addClassification(objC);
                added = true;
            }

        }

        return added;
    }

    /**
     * Remove all ObjectClassifications reference the classification c
     * @param c - Classification to remove
     * @return
     */
    default boolean removeClassification(Classification c) {
        boolean removed = false;
        if (this.getClassifications() != null) {
            Iterator<ObjectClassification> ocIter = this.getClassifications().iterator();
            while (ocIter.hasNext()) {
                ObjectClassification oc = ocIter.next();
                if (oc.getClassification().equals(c)) {
                    ocIter.remove();
                    removed = true;
                }
            }
        }
        return removed;
    }

}
