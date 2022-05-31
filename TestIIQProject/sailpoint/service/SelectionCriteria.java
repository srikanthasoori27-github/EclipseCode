/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;

/**
 * SelectionCriteria object to hold the details of id selections while making a decision
 *
 */
public class SelectionCriteria {

        boolean selectAll;
        List<String> selections;
        List<String> exclusions;
        String filter;
        List<SelectionCriteria> criteriaGroup;

        /**
         * Constructor
         */
        public SelectionCriteria() {}

        /** 
         * Constructor
         * @param selections List of ids selected for the decision
         */
        public SelectionCriteria(List<String> selections) {
            this.selections = selections;
        }

        /**
         * Constructor
         * @param selectionModel SelectionModel representing selections or exclusions
         */
        public SelectionCriteria(SelectionModel selectionModel) {
            this.selectAll = selectionModel.isSelectAll();
            if (selectionModel.isInclude()) {
                this.selections = selectionModel.getItemIds();
            } else {
                this.exclusions = selectionModel.getItemIds();
            }
            if (!Util.isEmpty(selectionModel.getGroups())) {
                this.criteriaGroup = new ArrayList<SelectionCriteria>();
                for (SelectionModel model : selectionModel.getGroups()) {
                    SelectionCriteria criteria = new SelectionCriteria(model);
                    this.criteriaGroup.add(criteria);
                }
            }
        }

        public boolean isBulk() {
            return this.selectAll || (Util.size(this.selections) > 1) || (Util.size(this.criteriaGroup) > 0);
        }

        public boolean isSelectAll() {
            return selectAll;
        }

        public void setSelectAll(boolean selectAll) {
            this.selectAll = selectAll;
        }

        public List<String> getSelections() {
            return selections;
        }

        public void setSelections(List<String> selections) {
            this.selections = selections;
        }

        public List<String> getExclusions() {
            return exclusions;
        }

        public void setExclusions(List<String> exclusions) {
            this.exclusions = exclusions;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }
    }