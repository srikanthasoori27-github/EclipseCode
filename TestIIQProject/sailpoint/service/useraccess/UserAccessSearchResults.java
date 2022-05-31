package sailpoint.service.useraccess;

import java.util.List;
import java.util.Map;

/**
 * Class to hold the results of a User Access Search
 */
public class UserAccessSearchResults {

    private int totalResultCount;
    private List<Map<String, Object>> results;

    /**
     * Constructor.
     * @param results List of Maps containing results of the search
     * @param totalResultCount Count of total possible results in the system
     */
    public UserAccessSearchResults(List<Map<String, Object>> results, int totalResultCount) {
        this.results = results;
        this.totalResultCount = totalResultCount;
    }

    /**
     * Get the count of total possible results in the system, limited by {@link UserAccessSearchOptions#getMaxResultCount()}
     */
    public int getTotalResultCount() {
        return totalResultCount;
    }

    /**
     * Get the List of Maps representing the results of this search
     */
    public List<Map<String, Object>> getResults() {
        return results;
    }
}