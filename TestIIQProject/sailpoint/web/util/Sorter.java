package sailpoint.web.util;

import sailpoint.object.QueryOptions;
import sailpoint.tools.Util;

import java.util.List;

/**
 * Class that represents sort information for use in REST requests and list services
 */
public class Sorter {

    public static String SORTER_DIRECTION_ASC = "ASC";
    public static String SORTER_DIRECTION_DESC = "DESC";

    private String property;
    private String direction;
    private boolean ignoreCase;
    private String sortProperty;
    private String secondarySort;
    
    public Sorter() {}

    /**
     * Constructor
     * @param property Object property
     * @param direction Direction
     * @param ignoreCase True to ignore case in search
     */
    public Sorter(String property, String direction, boolean ignoreCase) {
        this.property = Util.getKeyFromJsonSafeKey(property);
        setDirection(direction);
        this.ignoreCase = ignoreCase;
    }

    /**
     * @return the object property
     */
    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    /**
     * Returns the sort direction if set otherwise default to ASC.
     *
     * @return the sort direction
     */
    public String getDirection() {
        return this.direction;
    }

    /**
     * Set the sort direction. If a null or empty value is provided default the direction to ASC.
     *
     * @param direction sort direction
     */
    public void setDirection(String direction) {
        this.direction = Util.isNotNullOrEmpty(direction) ? direction : SORTER_DIRECTION_ASC;
    }

    /**
     * @return true if sort should ignore case
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    /**
     * @return the sort property, if defined, otherwise the object property
     */
    public String getSortProperty() {
        return (sortProperty == null) ? getProperty() : sortProperty;
    }

    public void setSortProperty(String sortProperty) {
        this.sortProperty = sortProperty;
    }

    /**
     * @return the secondary sort property, if defined, otherwise null
     */
    public String getSecondarySort() {
        return secondarySort;
    }

    public void setSecondarySort(String secondarySort) {
        this.secondarySort = secondarySort;
    }

    /**
     * Static method to check for ascending
     * @param direction String direction
     * @return true if the sort direction is ascending.
     */
    public static boolean isAscending(String direction) {
        return Util.nullSafeCaseInsensitiveEq(direction, SORTER_DIRECTION_ASC);
    }
    
    /**
     * @return true if the sort direction is ascending.
     */
    public boolean isAscending() {
        return isAscending(this.direction);
    }

    /**
     * Add the orderings defined by this SortOrder to the given QueryOptions.
     *
     * @param qo The QueryOptions to add the sorting.
     */
    public void addToQueryOptions(QueryOptions qo) {
        if (qo != null) {
            addSort(qo, this.getSortProperty(), this.isAscending(), this.isIgnoreCase());
            addSort(qo, this.getSecondarySort(), this.isAscending(), this.isIgnoreCase());
        }
    }

    /**
     * Add the give sort to the QueryOptions.
     *
     * @param qo          The QueryOptions to which to add the sorting.
     * @param colString   A csv or single column to sort by.
     * @param ascending   Whether to sort ascending.
     * @param ignoreCase  Whether to ignore case when sorting.
     */
    private static void addSort(QueryOptions qo, String colString,
                                boolean ascending, boolean ignoreCase) {
        if (null != colString) {
            List<String> properties = Util.csvToList(colString);
            if (null != properties) {
                for (String col : properties) {
                    qo.addOrdering(col, ascending, ignoreCase);
                }
            }
        }
    }
}