/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.timePeriod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.TimePeriod;
import sailpoint.object.TimePeriod.ClassifierType;
import sailpoint.tools.GeneralException;
import sailpoint.web.PageCodeBase;
import sailpoint.web.messages.MessageKeys;

public class TimePeriodState extends PageCodeBase {

    private Log log = LogFactory.getLog(TimePeriodState.class);
    
    private String editedTimePeriodId;
    private TimePeriod editedTimePeriod;
    private Map<String, Boolean> selectedDate;
    private Map<String, Boolean> selectedDaysOfWeek;
    private Map<String, Date> dates;
    private List<String> dateKeys;
    
    private List<String> availableDaysOfTheWeek;
    private static final List<String> normalizedDaysOfTheWeek;
    
    static {
        String [] days = { 
            MessageKeys.SUNDAY.toLowerCase(),
            MessageKeys.MONDAY.toLowerCase(),
            MessageKeys.TUESDAY.toLowerCase(),
            MessageKeys.WEDNESDAY.toLowerCase(),
            MessageKeys.THURSDAY.toLowerCase(),
            MessageKeys.FRIDAY.toLowerCase(),
            MessageKeys.SATURDAY.toLowerCase()
        };
        
        normalizedDaysOfTheWeek = Collections.unmodifiableList(Arrays.asList(days));
    }
    
    
    private Date newDate;
    
    public TimePeriodState() {
        editedTimePeriodId = "";
        editedTimePeriod = null;
        selectedDate = null;
        selectedDaysOfWeek = null;
        newDate = new Date();
        dates = null;

        
        String [] daysOfTheWeek = { 
            getMessage(MessageKeys.SUNDAY),
            getMessage(MessageKeys.MONDAY),
            getMessage(MessageKeys.TUESDAY),
            getMessage(MessageKeys.WEDNESDAY),
            getMessage(MessageKeys.THURSDAY),
            getMessage(MessageKeys.FRIDAY),
            getMessage(MessageKeys.SATURDAY)
        };

        availableDaysOfTheWeek = Arrays.asList(daysOfTheWeek);
    }
    
    public Date getNewDate() {
        return newDate;
    }

    public void setNewDate(Date newDate) {
        this.newDate = newDate;
    }

    public String getEditedTimePeriodId() {
        return editedTimePeriodId;
    }

    public void setEditedTimePeriodId(String editedTimePeriodId) {
        this.editedTimePeriodId = editedTimePeriodId;
    }
    
    @SuppressWarnings("unchecked")
    public TimePeriod getEditedTimePeriod() {
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            editedTimePeriod = ctx.getObjectById(TimePeriod.class, editedTimePeriodId);
            
            if (editedTimePeriod != null) {
                if (selectedDaysOfWeek == null && editedTimePeriod.getClassifier() == ClassifierType.DaysOfWeek) {
                    initSelectedDaysOfWeek();
                }
                
                if (dates == null && editedTimePeriod.getClassifier() == ClassifierType.DateSet) {
                    initDateSet();
                }
            }
            
        } catch (GeneralException e) {
            log.error("Time Periods cannot be edited at this time.", e);
        }
        
        return editedTimePeriod;
    }
    
    public List<String> getAvailableDaysOfTheWeek() {
        return availableDaysOfTheWeek;
    }
    
    public void setEditedTimePeriod(TimePeriod timePeriod) {
        editedTimePeriod = timePeriod;
    }

    public Map<String, Boolean> getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(Map<String, Boolean> selectedDate) {
        this.selectedDate = selectedDate;
    }
    
    public Map<String, Boolean> getSelectedDaysOfWeek() {
        return selectedDaysOfWeek;
    }

    public void setSelectedDaysOfWeek(Map<String, Boolean> selectedDaysOfWeek) {
        this.selectedDaysOfWeek = selectedDaysOfWeek;
    }
    
    public Map<String, Date> getDates() {
        return dates;
    }
    
    public void setDates(Map<String, Date> dates) {
        this.dates = dates;
    }
    
    public List<String> getDateKeys() {
        return dateKeys;
    }
    
    public String saveEdits() {
        try {
            applySelectedDaysOfWeek();
            applyDateSetChanges();
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            ctx.saveObject(editedTimePeriod);
            ctx.commitTransaction();
            editedTimePeriodId = "";
            editedTimePeriod = null;
            selectedDaysOfWeek = null;
        } catch (GeneralException e) {
            log.error("The time period cannot be saved at this time.", e);
        }
        
        return "save";
    }
    
    public String cancelEdits() {
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            ctx.rollbackTransaction();
            editedTimePeriodId = "";
            editedTimePeriod = null;
            selectedDaysOfWeek = null;
        } catch (GeneralException e) {
            log.error("The time period cannot be restored at this time.", e);
        }
        
        return "cancel";
    }

    @SuppressWarnings("unchecked")
    public String deleteSelectedDates() {
        if (editedTimePeriod != null && editedTimePeriod.getClassifier() == ClassifierType.DateSet) {            
            List<Date> keptDates = new ArrayList<Date>();
            Set<String> dateSelectionKeys = selectedDate.keySet();
            
            for (String dateKey : dateSelectionKeys) {
                boolean isDateSelected = selectedDate.get(dateKey);
                if (!isDateSelected) {
                    keptDates.add(dates.get(dateKey));
                }
            }
            
            Map<String, Object> existingAttributes = editedTimePeriod.getInitParameters().getMap();
            existingAttributes.put("dates", keptDates);
            
            editedTimePeriod.getInitParameters().setMap(existingAttributes);
            
            try {
                SailPointContext ctx = SailPointFactory.getCurrentContext();
                ctx.saveObject(editedTimePeriod);
                ctx.commitTransaction();
                // Force a reload on the date map.  See the comments for initDateSet() for details
                dates = null;
            } catch (GeneralException e) {
                log.error("The time period cannot be saved at this time.", e);
            }
        }
        
        return "deleteDates";
    }
    
    @SuppressWarnings("unchecked")
    public String addDate() {
        if (editedTimePeriod != null && editedTimePeriod.getClassifier() == ClassifierType.DateSet) {
            applyDateSetChanges();
            Map<String, Object> existingAttributes = editedTimePeriod.getInitParameters().getMap();
            
            List<Date> storedDates = editedTimePeriod.getInitParameters().getList("dates");
                        
            if (newDate != null && !storedDates.contains(newDate)) {
                storedDates.add(newDate);
            }
            
            existingAttributes.put("dates", storedDates);
            
            editedTimePeriod.getInitParameters().setMap(existingAttributes);
            
            try {
                SailPointContext ctx = SailPointFactory.getCurrentContext();
                ctx.saveObject(editedTimePeriod);
                ctx.commitTransaction();
                // Force a reload on the date map.  See the comments for initDateSet() for details
                dates = null;
            } catch (GeneralException e) {
                log.error("The time period cannot be saved at this time.", e);
            }
        }
        
        return "addDate";
    }
    
    public String goBack() {
        editedTimePeriodId = "";
        editedTimePeriod = null;
        selectedDaysOfWeek = null;
        return "goBack";
    }
    
    @SuppressWarnings("unchecked")
    private void initSelectedDaysOfWeek() {
        selectedDaysOfWeek = new HashMap<String, Boolean>();
        for (String dayOfTheWeek : availableDaysOfTheWeek) {
            selectedDaysOfWeek.put(dayOfTheWeek, false);
        }
        
        List<String> selectedDays = editedTimePeriod.getInitParameters().getList("days");
          
        if (selectedDays != null) {
            for (String selectedDay : selectedDays) {
                selectedDaysOfWeek.put(getMessage(selectedDay), true);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void applySelectedDaysOfWeek() {
        if (editedTimePeriod != null && editedTimePeriod.getClassifier() == ClassifierType.DaysOfWeek) {
            List<String> currentDays = editedTimePeriod.getInitParameters().getList("days");
            Set<String> daySelections = selectedDaysOfWeek.keySet();
            
            // Here's where it gets a little confusing.  We have two lists:  one is presented to the 
            // user in the UI and one resides in our configuration.  The former is internationalized 
            // and the latter is normalized to English.  Whenever we get something that comes from
            // the UI we have to look up its English equivalent in order to properly persist it.
            Map<String, String> weekdayKeyMap = getInternationalizedWeekdayMap();
            
            Map<String, Object> attributes = editedTimePeriod.getInitParameters().getMap();
            
            if (null == currentDays) {   // There may be no days of the week selected.
                currentDays = new ArrayList<String>();
            }
            
            for (String daySelection : daySelections) {
                // To help keep track of what we're doing:
                // daySelection is coming from the UI so it's internationalized
                // currentDays is the list that is coming from our configuration so it is not
                boolean isSelected = selectedDaysOfWeek.get(daySelection);
                
                String normalizedDaySelection = weekdayKeyMap.get(daySelection);
                if (!isSelected && currentDays.contains(normalizedDaySelection)) {
                    currentDays.remove(normalizedDaySelection);
                } else if (isSelected && !currentDays.contains(normalizedDaySelection)) {
                    currentDays.add(normalizedDaySelection);
                }
            }
                    
            Collections.sort(currentDays, new Comparator() {
                public int compare(Object o1, Object o2) {
                    if (o1 instanceof String && o2 instanceof String) {
                        String day1 = ((String) o1).toLowerCase();
                        String day2 = ((String) o2).toLowerCase();
                        if (normalizedDaysOfTheWeek.contains(day1) && normalizedDaysOfTheWeek.contains(day2)) {
                            int day1Int = normalizedDaysOfTheWeek.indexOf(day1);
                            int day2Int = normalizedDaysOfTheWeek.indexOf(day2);
                            return day1Int - day2Int;
                        } else {
                            throw new IllegalArgumentException("We can only sort on days of the week in English");
                        }
                    } else {
                        throw new IllegalArgumentException("We can only sort on days of the week in English");
                    }
                }
            });
                
            attributes.put("days", currentDays);
            editedTimePeriod.getInitParameters().setMap(attributes);
        }
    }
    
    
    // This Date stuff is ugly.  JSF won't apply changes to items in a list.  However, it will
    // apply changes to items in a Map, so instead of directly returning the Dates as a List, 
    // we put the Dates in a dummied-up Map and return keys instead.  The page then uses the
    // keys to get the Date objects, which it can then edit.  Hooray for indirection!!!
    // --Bernie Margolis
    @SuppressWarnings("unchecked")
    private void initDateSet() {
        if (editedTimePeriod != null && editedTimePeriod.getClassifier() == ClassifierType.DateSet) {
            selectedDate = new HashMap<String, Boolean>();
            dates = new HashMap<String, Date>();
            dateKeys = new ArrayList<String>();
            List<Date> dateParams = editedTimePeriod.getInitParameters().getList("dates");
            if (null != dateParams) {  // In case all dates have been deleted
                SortedSet<Date> sortedDateParams = new TreeSet<Date>(dateParams);
                
                int i = 0;
                
                for (Date date : sortedDateParams) {
                    String dateKey = String.valueOf(i);
                    dates.put(dateKey, date);
                    dateKeys.add(dateKey);
                    i++;
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void applyDateSetChanges() {
        if (editedTimePeriod != null && editedTimePeriod.getClassifier() == ClassifierType.DateSet) {
           List<Date> newDateVals = new ArrayList<Date>();
           newDateVals.addAll(dates.values());
           Map<String, Object> attributes = editedTimePeriod.getInitParameters().getMap();
           attributes.put("dates", newDateVals);
           editedTimePeriod.getInitParameters().setMap(attributes);
        }
    }
    
    /**
     * @return a Map of weekday message keys.  The keys in the Map are the internationalized 
     * strings that correspond to the message keys
     */
    private Map<String, String> getInternationalizedWeekdayMap() {
        Map<String, String> weekdayMap = new HashMap<String, String>();
        weekdayMap.put(getMessage(MessageKeys.SUNDAY), MessageKeys.SUNDAY);
        weekdayMap.put(getMessage(MessageKeys.MONDAY), MessageKeys.MONDAY);
        weekdayMap.put(getMessage(MessageKeys.TUESDAY), MessageKeys.TUESDAY);
        weekdayMap.put(getMessage(MessageKeys.WEDNESDAY), MessageKeys.WEDNESDAY);
        weekdayMap.put(getMessage(MessageKeys.THURSDAY), MessageKeys.THURSDAY);
        weekdayMap.put(getMessage(MessageKeys.FRIDAY), MessageKeys.FRIDAY);
        weekdayMap.put(getMessage(MessageKeys.SATURDAY), MessageKeys.SATURDAY);
        return weekdayMap;
    }
    
}
