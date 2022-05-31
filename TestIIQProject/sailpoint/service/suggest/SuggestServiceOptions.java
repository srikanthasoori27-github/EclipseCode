/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.suggest;

import sailpoint.object.Filter;

public class SuggestServiceOptions {
    private String column;
    private String direction;
    private boolean lcm;
    private String lcmAction;
    private String lcmQuicklinkName;
    private Filter additionalFilter;
    private String targetIdentityId;
    private boolean exclude;

    public SuggestServiceOptions() {
        lcm = false;
        exclude = true;
    }

    public static SuggestServiceOptions getInstance() {
        return new SuggestServiceOptions();
    }

    public String getColumn() {
        return column;
    }

    public SuggestServiceOptions setColumn(String column) {
        this.column = column;
        return this;
    }

    public String getDirection() {
        return direction;
    }

    public SuggestServiceOptions setDirection(String direction) {
        this.direction = direction;
        return this;
    }

    public boolean isLcm() {
        return lcm;
    }

    public SuggestServiceOptions setLcm(boolean lcm) {
        this.lcm = lcm;
        return this;
    }

    public String getLcmAction() {
        return lcmAction;
    }

    public SuggestServiceOptions setLcmAction(String lcmAction) {
        this.lcmAction = lcmAction;
        return this;
    }

    public String getLcmQuicklinkName() {
        return lcmQuicklinkName;
    }

    public SuggestServiceOptions setLcmQuicklinkName(String lcmQuicklinkName) {
        this.lcmQuicklinkName = lcmQuicklinkName;
        return this;
    }

    public Filter getAdditionalFilter() {
        return additionalFilter;
    }

    public SuggestServiceOptions setAdditionalFilter(Filter additionalFilter) {
        this.additionalFilter = additionalFilter;
        return this;
    }

    public String getTargetIdentityId() {
        return targetIdentityId;
    }

    public SuggestServiceOptions setTargetIdentityId(String targetIdentityId) {
        this.targetIdentityId = targetIdentityId;
        return this;
    }

    public boolean isExclude() {
        return exclude;
    }

    public SuggestServiceOptions setExclude(boolean exclude) {
        this.exclude = exclude;
        return this;
    }
}
