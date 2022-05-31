/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class WorkItemDataSource extends SailPointUnionDataSource<SailPointObject> {
    private static final Log log = LogFactory.getLog(WorkItemDataSource.class);
    // This value contains the index for the iterator containing the object that is currently last in the queue.
    // We keep this around so we know which iterator to pull values off of when trying to find a new object to be
    // last in the queue
    public WorkItemDataSource(Class[] includedScopes, Map<Class, List<Filter>> filters, Locale locale, TimeZone timezone) {
        super(includedScopes, filters, locale, timezone);
        if (includedScopes != null && includedScopes.length > 2) {
            throw new IllegalArgumentException("The WorkItemDataSource only supports two scopes:  WorkItem and WorkItemArchive.  " + includedScopes.length + " scopes were passed to the WorkItemDataSource.");
        }
        qo.addOrdering("name", true);
        // Exclude events from reports
        qo.add(Filter.not(Filter.eq("type", WorkItem.Type.Event)));
    }

    @Override
    public void internalPrepare() throws GeneralException {
        // Default to getting everything if no scope was given
        if (_scopes == null || _scopes.length == 0) {
            _scopes = new Class[] {WorkItem.class, WorkItemArchive.class};
        }
    }

    @Override
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        
        Object value = null;
        if (fieldName.equals("state")) {
            WorkItem.State state;
            if (_object instanceof WorkItem) {
                state = ((WorkItem)_object).getState();
            } else if (_object instanceof WorkItemArchive) {
                state = ((WorkItemArchive)_object).getState();
            } else {
                throw new IllegalArgumentException("The WorkItemDataSource attempted to process an illegal object: " + _object);
            }
            Message stateName = new Message(state == null ? MessageKeys.WORK_ITEM_STATE_OPEN : state.getMessageKey());
            value = stateName.getLocalizedMessage(getLocale(), null);
        } else if (fieldName.equals("type")) {
            WorkItem.Type type;
            if (_object instanceof WorkItem) {
                type = ((WorkItem)_object).getType();
            } else if (_object instanceof WorkItemArchive) {
                type = ((WorkItemArchive)_object).getType();
            } else {
                throw new IllegalArgumentException("The WorkItemDataSource attempted to process an illegal object: " + _object);
            }
            if (type != null) {
                Message typeName = new Message(type.getMessageKey());
                value = typeName.getLocalizedMessage(getLocale(), null);
            }
        } else if (fieldName.equals("archiveStatus")) {
            if (_object instanceof WorkItem) {
                value = new Message(MessageKeys.WORK_ITEM_STATUS_ACTIVE).getLocalizedMessage(getLocale(), getTimezone());
            } else if (_object instanceof WorkItemArchive) {
                value = new Message(MessageKeys.WORK_ITEM_STATUS_ARCHIVED).getLocalizedMessage(getLocale(), getTimezone());
            } else {
                throw new IllegalArgumentException("The WorkItemDataSource attempted to process an illegal object: " + _object);
            }
        } else if (fieldName.equals("name")) {
            // Strip the leading zeroes off of the ID.
            value = Util.stripLeadingChar(_object.getName(), '0');
        } else if (fieldName.equals("level")) {
            WorkItem.Level priority;
            
            if (_object == null) {
                priority = null;
            } else if (_object instanceof WorkItem) {
                priority = ((WorkItem)_object).getLevel();
            } else if (_object instanceof WorkItemArchive) {
                priority = ((WorkItemArchive)_object).getLevel();
            } else {
                priority = null;
            }

            if (priority == null) {
                priority = WorkItem.Level.Normal;
            }
            
            value = new Message(priority.getMessageKey()).getLocalizedMessage(getLocale(), getTimezone());
        }

        if (value == null)
            value = super.getFieldValue(jrField);

        return value;
    }

    @Override
    public boolean internalNext() throws JRException {
        // Ordering is by owner name first and then by name
        boolean hasMore = false;
        
        if (_candidates != null && _candidates.length > 0) {
            for (int i = 0; i < _candidates.length; ++i) {
                hasMore |= (_candidates[i] != null);
            }
        }
            
        
        if (hasMore) {
            // Pick a winner
            Winner winner = pickAWinner();
            
            if (winner == null) {
                // The logic at the beginning of this method should prevent this from happening, but let's be sure
                hasMore = false;                
            } else {
                // We have a winner -- promote it to the current object and replace the winning candidate with the next
                // candidate in the iterator
                _object = winner.object;
                Iterator<SailPointObject> winningIterator = (Iterator<SailPointObject>)_objects[winner.index];
                if (winningIterator.hasNext()) {
                    _candidates[winner.index] = winningIterator.next();
                } else {
                    _candidates[winner.index] = null;
                }
            }
        }
        
        return hasMore;
    }
    
    /*
     * Encapsulates information about the object that should currently be returned by the data source
     */
    private class Winner {
        /* The object that should currently be returned from the data source */
        SailPointObject object;
        /* Index of the iterator and candidate containing the winning object */
        int index;
        
        private Winner(SailPointObject object, int index) {
            this.object = object;
            this.index = index;
        }
    }
    
    /*
     * Determine which of the candidates should be used to populate the _object and return it
     */
    private Winner pickAWinner() {
        Winner winner = null;
        
        for (int i = 0; i < _candidates.length; ++i) {
            SailPointObject challenger = _candidates[i];
            if (winner == null) {
                // If there's no current winner the challenger wins by default,
                // unless there were no challengers left in this iterator
                // in which case just leave the winner as null
                if (challenger != null) {
                    winner = new Winner(challenger, i);
                }
            } else {
                // If there's no challenger always keep the current winner.  Otherwise they duke it out
                if (challenger != null) {
                    int compare = compareIds(winner, challenger);
                    if (compare == 0) {
                        compare = compareNames(winner, challenger);
                    }
                      
                    if (compare > 0) {
                        winner = new Winner(challenger, i);
                    }
                }
            }
        }
        
        return winner;
    }
    
    private int compareIds(Winner winner, SailPointObject challenger) {
        String winnerId = null;
        if (winner.object != null) {
            winnerId = winner.object.getName();
        }
        if (winnerId == null) {
            winnerId = "-1";
        }
        
        String challengerId = null;
        if (challenger != null) {
            challengerId = challenger.getName();
        }
        if (challengerId == null) {
            challengerId = "-1";
        }
        
        int winnerIntId = Integer.parseInt(winnerId);
        int challengerIntId = Integer.parseInt(challengerId);
        
        return winnerIntId - challengerIntId;
    }
    
    /*
     * Compare the names to see whether the winner or challenger should come first.  The comparison is done by owner name
     * or by plain name if the owner names match exactly.
     */
    private int compareNames(Winner winner, SailPointObject challenger) {
        String winnerName = null;
        if (winner.object.getOwner() != null) {
            winnerName = winner.object.getOwner().getName();
        }
        
        if (winnerName == null) {
            winnerName = "";
        }
        
        String challengerName = null;
        if (challenger.getOwner() != null) {
            challengerName = challenger.getOwner().getName();
        }
        if (challengerName == null) {
            challengerName = "";
        }
        
        int nameCompare = winnerName.compareToIgnoreCase(challengerName);
        
        return nameCompare;
    }
}
