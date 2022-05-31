/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Alert;
import sailpoint.object.Filter;
import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by ryan.pickens on 8/23/16.
 */
public class AlertFilterContext extends BaseListFilterContext {

    public AlertFilterContext() {
        super(ObjectConfig.getObjectConfig(Alert.class));
    }

    private static final String ALERT_CLASS = Alert.class.getSimpleName();
    private static final String PROPERTY_ACTIONS = "actions";
    private static final String PROPERTY_SOURCE = "source";
    private static final String PROPERTY_ALERT_START = "alertDateStart";
    private static final String PROPERTY_ALERT_END = "alertDateEnd";
    private static final String PROPERTY_PROCESSED_START = "lastProcessedStart";
    private static final String PROPERTY_PROCESSED_END = "lastProcessedEnd";
    private static final String PROPERTY_NAME = "name";

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale)
        throws GeneralException {

        List<ListFilterDTO> filters = new ArrayList<>();

        ListFilterDTO nameFilter = new ListFilterDTO("name", MessageKeys.UI_ALERT_FILTER_NAME, false, ListFilterDTO.DataTypes.String, locale);
        filters.add(nameFilter);

        ListFilterDTO sourceFilter = new ListFilterDTO("source", MessageKeys.UI_ALERT_FILTER_SOURCE, false, ListFilterDTO.DataTypes.Column, locale);
        sourceFilter.configureColumnSuggest(ALERT_CLASS, "source.name", null, getSuggestUrl());
        filters.add(sourceFilter);

        ListFilterDTO nativeIdFilter = new ListFilterDTO("nativeId", MessageKeys.UI_ALERT_FILTER_NATIVEID, false, ListFilterDTO.DataTypes.String, locale);
        filters.add(nativeIdFilter);

        ListFilterDTO typeFilter = new ListFilterDTO("type", MessageKeys.UI_ALERT_FILTER_TYPE, false, ListFilterDTO.DataTypes.Column, locale);
        typeFilter.configureColumnSuggest(ALERT_CLASS, "type", null, getSuggestUrl());
        filters.add(typeFilter);

        ListFilterDTO targetTypeFilter = new ListFilterDTO("targetType", MessageKeys.UI_ALERT_FILTER_TARGET_TYPE, false, ListFilterDTO.DataTypes.Column, locale);
        targetTypeFilter.configureColumnSuggest(ALERT_CLASS, "targetType", null, getSuggestUrl());
        filters.add(targetTypeFilter);

        ListFilterDTO targetDisplayNameFilter = new ListFilterDTO("targetDisplayName", MessageKeys.UI_ALERT_FILTER_TARGET_NAME, false, ListFilterDTO.DataTypes.String, locale);
        filters.add(targetDisplayNameFilter);

        ListFilterDTO alertStartDateFilter = new ListFilterDTO("alertDateStart", MessageKeys.UI_ALERT_FILTER_ALERT_DATE_START, false, ListFilterDTO.DataTypes.Date, locale);
        filters.add(alertStartDateFilter);

        ListFilterDTO alertEndDateFilter = new ListFilterDTO("alertDateEnd", MessageKeys.UI_ALERT_FILTER_ALERT_DATE_END, false, ListFilterDTO.DataTypes.Date, locale);
        filters.add(alertEndDateFilter);

        ListFilterDTO lastProcessedStart = new ListFilterDTO("lastProcessedStart", MessageKeys.UI_ALERT_FILTER_LAST_PROCESSED_START, false, ListFilterDTO.DataTypes.Date, locale);
        filters.add(lastProcessedStart);

        ListFilterDTO lastProcessedEnd = new ListFilterDTO("lastProcessedEnd", MessageKeys.UI_ALERT_FILTER_LAST_PROCESSED_END, false, ListFilterDTO.DataTypes.Date, locale);
        filters.add(lastProcessedEnd);

        ListFilterDTO actionsFilter = new ListFilterDTO("actions", MessageKeys.UI_ALERT_FILTER_ACTIONS, false, ListFilterDTO.DataTypes.Boolean, locale);
        filters.add(actionsFilter);


        return filters;
    }


    @Override
    public Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context)
        throws GeneralException {
        Filter filter = null;

        if (filterDTO != null && PROPERTY_ACTIONS.equals(filterDTO.getProperty())) {
            if (filterValue.getValue() != null) {
                if (Util.otob(filterValue.getValue())) {
                    filter = Filter.not(Filter.isempty("actions"));
                } else {
                    //False set, return just those without actions
                    filter = Filter.isempty("actions");
                }
            }
        } else if (filterDTO != null && PROPERTY_SOURCE.equals(filterDTO.getProperty())) {
            if (filterValue.getValue() != null) {
                filter = Filter.eq("source.name", getValueString(filterValue));
            }
        } else if (filterDTO != null && PROPERTY_ALERT_START.equals(filterDTO.getProperty())) {
            String value = getValueString(filterValue);
            if (Util.isNotNullOrEmpty(value)) {
                Date alertStart = getDateFromParam(value, 0);
                if (alertStart != null) {
                    filter = Filter.ge("alertDate", alertStart);
                }
            }

        } else if (filterDTO != null && PROPERTY_ALERT_END.equals(filterDTO.getProperty())) {

            String value = getValueString(filterValue);
            if (Util.isNotNullOrEmpty(value)) {
                Date alertEnd = getDateFromParam(value, 1);
                if (alertEnd != null) {
                    filter = Filter.le("alertDate", alertEnd);
                }
            }
        } else if (filterDTO != null && PROPERTY_PROCESSED_START.equals(filterDTO.getProperty())) {

            String value = getValueString(filterValue);
            if (Util.isNotNullOrEmpty(value)) {
                Date processedStart = getDateFromParam(value, 0);
                if (processedStart != null) {
                    filter = Filter.ge("lastProcessed", processedStart);
                }
            }
        } else if (filterDTO != null && PROPERTY_PROCESSED_END.equals(filterDTO.getProperty())) {

            String value = getValueString(filterValue);
            if (Util.isNotNullOrEmpty(value)) {
                Date processedEnd = getDateFromParam(value, 1);
                if (processedEnd != null) {
                    filter = Filter.le("lastProcessed", processedEnd);
                }
            }
        } else if (filterDTO != null && PROPERTY_NAME.equals(filterDTO.getProperty())) {
            filter = Filter.eq(filterDTO.getProperty(), sailpoint.tools.Util.padID(getValueString(filterValue)));
        } else {
            filter = super.convertFilter(filterDTO, filterValue, context);
        }

        return filter;
    }


    /**
     * Gets a date from the string parameter.
     *
     * @param param The string parameter.
     * @param idx The token index to parse.
     * @return The date or null if invalid.
     */
    private Date getDateFromParam(String param, int idx) {
        Date date = null;

        String[] dateTokens = param.split("\\|");
        if (dateTokens.length > idx) {
            Date parsedDate = sailpoint.tools.Util.getDate(dateTokens[idx]);
            if (parsedDate != null) {
                date = parsedDate;
            }
        } else if (dateTokens.length == 1) {
            //Use the first
            Date parsedDate = sailpoint.tools.Util.getDate(dateTokens[0]);
            if (parsedDate != null) {
                date = parsedDate;
            }
        }


        return date;
    }

}
