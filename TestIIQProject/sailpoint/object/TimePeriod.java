/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.faces.context.FacesContext;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.PageCodeBase;

@XMLClass
public class TimePeriod extends SailPointObject implements Comparable {
    private static final long serialVersionUID = -2486945622176300782L;

    private ClassifierType classifier;
    private Attributes initParameters;
    
    private static final String TIME_FORMAT = "h:mm a";
    
    @XMLClass(xmlname="ClassifierType")
    public enum ClassifierType {
        DateRange,
        DateSet,
        DaysOfWeek,
        TimeOfDayRange
    };

    //hide mutable class
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes getInitParameters() {
        return initParameters;
    }

    @SuppressWarnings("unchecked")
    public void setInitParameters(Attributes initParameters) {
        this.initParameters = initParameters;
    }

    @XMLProperty
    public ClassifierType getClassifier() {
        return classifier;
    }

    public void setClassifier(ClassifierType classifier) {
        this.classifier = classifier;
    }

    public int compareTo(Object arg0) {
        int result = -1;
        if ( arg0 != null ) {
            if (arg0 instanceof TimePeriod) {
                if ( arg0 != null ) {
                    String thisName = getName();
                    String thatName = ((TimePeriod) arg0).getName();
                    result = Util.nullSafeCompareTo(thisName, thatName);
                } 
            } else {
                throw new IllegalArgumentException("TimePeriods cannot be compared to instances of " + arg0.getClass().getName());
            }
        }
        return result;
    }
    
    /**
     * This helper method returns a formatted version of the initParameters for display in the UI
     */  
    @SuppressWarnings("unchecked")
    public Attributes getFormattedInitParameters() throws GeneralException {
        Map<String, Object> formattedAttributeMap = new HashMap<String, Object>();
        
        FacesContext context = FacesContext.getCurrentInstance();
        Locale locale = context.getViewRoot().getLocale();
        TimeZone timeZone = (TimeZone) context.getExternalContext().getSessionMap().get(PageCodeBase.SESSION_TIMEZONE);;
        if (timeZone == null)
            timeZone = TimeZone.getDefault();
        
        if (classifier == ClassifierType.DateRange) {
            Date startDate = initParameters.getDate("startDate");
            formattedAttributeMap.put("startDate", Internationalizer.getLocalizedDate(startDate, locale, timeZone));
            Date endDate = initParameters.getDate("endDate");
            formattedAttributeMap.put("endDate", Internationalizer.getLocalizedDate(endDate, locale, timeZone));
        } else if (classifier == ClassifierType.DateSet) {
            List<Date> dates = (List<Date>)initParameters.getList("dates");
            List<String> formattedDates = new ArrayList<String>();
            
            if (null != dates) {
                SortedSet<Date> sortedDates = new TreeSet<Date>(dates);
                for (Date date : sortedDates) {
                    String formattedDate = Internationalizer.getLocalizedDate(date, locale, timeZone);
                    formattedDates.add(formattedDate);
                }
            } else {
                // if no holidays have been selected, just print the message key for "None".
                formattedDates.add(Internationalizer.getMessage("none", locale));
            }
            
            formattedAttributeMap.put("dates", formattedDates);
        } else if (classifier == ClassifierType.DaysOfWeek) {
            List<String> days = (List<String>)initParameters.getList("days");
            List<String> formattedDays = new ArrayList<String>();
            
            if (null != days) {
                for (String day : days) {
                    String formattedDay = Internationalizer.getMessage(day, locale);
                    formattedDays.add(formattedDay);
                }
            } else {
                // if no days have been selected, just print the message key for "None".
                formattedDays.add(Internationalizer.getMessage("none", locale));
            }
            formattedAttributeMap.put("days", formattedDays);
        } else if (classifier == ClassifierType.TimeOfDayRange) {
            Date startTime = initParameters.getDate("startTime");
            Date endTime = initParameters.getDate("endTime");
            formattedAttributeMap.put("startTime", Util.dateToString(startTime, TIME_FORMAT, timeZone));
            formattedAttributeMap.put("endTime", Util.dateToString(endTime, TIME_FORMAT, timeZone));
        } else {
            throw new IllegalArgumentException("The UI does not recognize the " + classifier.toString() + " classifier.");
        }
        
        return new Attributes(formattedAttributeMap);
    }
    
    /**
     * This helper method converts date objects to time strings and replaces the specified
     * parameter with the resulting value
     */ 
    @SuppressWarnings("unchecked")
    public void setTimeAttribute(final String whichAttribute, final Date whatTime) {
        FacesContext context = FacesContext.getCurrentInstance();
        TimeZone timeZone = (TimeZone) context.getExternalContext().getSessionMap().get(PageCodeBase.SESSION_TIMEZONE);;
        if (timeZone == null)
            timeZone = TimeZone.getDefault();

        if (getClassifier() == ClassifierType.TimeOfDayRange) {
            String startTimeString = Util.dateToString(whatTime, TIME_FORMAT, timeZone);
            Attributes attrs = getInitParameters();
            Map modifiedAttrs = new HashMap<String, Object>(attrs.getMap());
            modifiedAttrs.put(whichAttribute, startTimeString);
            attrs.setMap(modifiedAttrs);
        }
    }
    
    /**
     * This helper method pulls a time value for the specified attribute out of the map
     * and returns it as a Date object
     */
    public Date getTimeStringAsDate(final String whichAttribute) {
        Date startTime;
        
        if (getClassifier() == ClassifierType.TimeOfDayRange) {
            String timeString = null;
            
            try {
                timeString = getInitParameters().getString(whichAttribute); 
                startTime = Util.stringToTime(timeString);
            } catch (ParseException e) {
                startTime = null;
            }
        } else {
            startTime = null;
        }
        
        return startTime;
    }

}
