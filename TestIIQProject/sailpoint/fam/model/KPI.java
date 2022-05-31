/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

public class KPI extends FAMObject implements SCIMObject {

    static String SCIM_PATH = "kpis";

    /**
     * Use this filter attribute to get statistics for FAM widgets. Supports the equal operator only.
     */
    public static final String FILTER_WIDGET_NAME = "name";
    public static final String COUNT = "count";
    public static final String SCORE = "score";

    int count;
    String score;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }

    @Override
    public String getSCIMPath() {
        return SCIM_PATH;
    }
}
