/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import sailpoint.api.DynamicValuator;
import sailpoint.api.SailPointContext;
import sailpoint.object.DynamicValue;
import sailpoint.object.ReportColumnConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Localizable;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class DataSourceColumnHelper {

    private Locale locale;
    private TimeZone timezone;
    private Map<String, Object> renderCache;

    public DataSourceColumnHelper(Locale locale, TimeZone timezone) {
        this.locale = locale;
        this.timezone = timezone;
        renderCache = new HashMap<String, Object>();
    }

    public Object runColumnRenderer(SailPointContext context, ReportColumnConfig col, Object val,
                                    Map<String, Object> scriptArgs) throws GeneralException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("value", val);
        args.put("context", context);
        args.put("column", col);
        args.put("scriptArgs", scriptArgs);
        args.put("locale", locale);
        args.put("timezone", timezone);
        args.put("renderCache", renderCache);

        DynamicValue renderer = col.getRenderDef();

        // make sure that any render rules are still
        // attached to the session or we could get a lazy init exception
        if (col.getRenderRule() != null){
            context.attach(col.getRenderRule());
        }

        DynamicValuator valuator = new DynamicValuator(renderer);
        return valuator.evaluate(context, args);
    }

    public Object getColumnValue(Object value, ReportColumnConfig col){

        Object myVal =  value;

        if (myVal != null && myVal instanceof Date){
            myVal = formatDate((Date)value, col);
        } else if (myVal != null && myVal instanceof java.util.List) {
            myVal = Util.listToQuotedCsv((List) myVal, null, true, true);
        }  else if (myVal != null && Localizable.class.isAssignableFrom(myVal.getClass())){
            Localizable localizable = (Localizable)myVal;
            myVal = localizable.getLocalizedMessage(locale, timezone);
        }  else if (myVal != null && MessageKeyHolder.class.isAssignableFrom(myVal.getClass())){
            MessageKeyHolder obj = (MessageKeyHolder)myVal;
            String msg = Internationalizer.getMessage(obj.getMessageKey(), locale);
            myVal = msg != null ? msg : obj;
        } else if (myVal instanceof String) {
            // The col could be null when creating ChartData, localize it if so
            // Otherwise if a columnConfig exists, check the localization flag
            if (col == null || !col.isSkipLocalization()) {
                String val = Internationalizer.getMessage((String)myVal, locale);
                if (val != null) {
                    myVal = val;
                }
            }
        }

        return myVal;
    }

    public String formatDate(Date val, ReportColumnConfig col) {
        String output = null;
        if (col != null && col.hasDateFormatting()) {
            if (col.isCustomDateStyle() || col.isCustomTimeStyle()) {

                String datePart = null;
                if (!col.isCustomDateStyle()) {
                    datePart = Internationalizer.getLocalizedDate(val, col.getDateStyle(),
                            null, locale, timezone);
                } else if (col.getDateFormat() != null) {
                    datePart = new SimpleDateFormat(col.getDateFormat()).format(val);
                }

                String timePart = null;
                if (!col.isCustomTimeStyle()) {
                    timePart = Internationalizer.getLocalizedDate(val, col.getDateStyle(),
                            null, locale, timezone);
                } else if (col.getTimeFormat() != null) {
                    timePart = new SimpleDateFormat(col.getTimeFormat()).format(val);
                }

                if (datePart != null) {
                    output = timePart != null ? datePart + " " + timePart : datePart;
                } else {
                    output = timePart;
                }

            } else if (col.getDateStyle() != null || col.getTimeStyle() != null) {
                output = Internationalizer.getLocalizedDate(val, col.getDateStyle(),
                        col.getTimeStyle(), locale, timezone);
            }
        } else {
            output = Internationalizer.getLocalizedDate(val, locale, timezone);
        }

        return output;
    }

}
