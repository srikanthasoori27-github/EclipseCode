/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Helper bean to wrap a GenericIndex and provide a cooked
 * representation of the component score definitions.
 *
 * This is used by both IdentityBean and ApplicationBean.
 *
 * Author: Jeff
 * 
 * Some scores are merged so we can present the distinction
 * between "raw" and "compensated" more easily than trying
 * to analyze the ScoreConfig in the JSF page.
 *
 * To determine if a score has a raw/composite pair we're looking
 * at the parent definition reference.  Scores with a parent are
 * assumed to be compensated and the parent is raw.  It might
 * be better to have a more formal definition so we could just
 * share definitions without the extra semantic baggage.
 *
 */

package sailpoint.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.object.GenericIndex;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class ScorecardDTO {

    GenericIndex _index;
    List<ScoreDTO> _scores;
    List<ScoreItemDTO> _scoreItems;
    boolean _compensated;

    public ScorecardDTO(GenericIndex index, List<ScoreDefinition> scores, Locale locale, TimeZone timezone) {

        _index = index;
        _scores = new ArrayList<ScoreDTO>();

        List<ScoreItem> i = _index.getScoreItems();

        if (scores != null) {
            List used = new ArrayList();

            // first add the raw/compensated pairs
            for (ScoreDefinition def : scores) {
                String parent = def.getParent();
                if (!def.isDisabled() && !def.isComposite() &&
                    parent != null) {

                    _compensated = true;

                    ScoreDefinition raw = ScoreDefinition.getScore(scores, parent);
                    // raw may be null if misconfigured, assume
                    // it should still show up in the pair list
                    _scores.add(new ScoreDTO(def, raw, index));
                            
                    // remember this so we don't use it in the 
                    // next loop
                    if (raw != null) used.add(raw);
                }
            }

            // then the uncompensated scores
            for (ScoreDefinition def : scores) {
                if (!def.isDisabled() && 
                    !def.isComposite() && 
                    def.getParent() == null && 
                    !used.contains(def)) {

                    _scores.add(new ScoreDTO(def, index));
                }
            }

            // create score localized items list
            // In 3.1 we changed the ScoreDefinition model so that
            // the "shortName" is a catalog key.  This will become the
            // "type" property of the ScoreItem.  Previously this did
            // a convoluted search trying to find a ScoreBean whose category
            // matched the ScoreItem.type.  I don't fully understand the
            // old way but it shouldn't be that hard.  The type name is either
            // a catalog key or the actual text to display.  This allows
            // us to not have to upgrade all the identity Scorecards to change
            // the original unlocalized shortName into a catalog key.

            if (_index != null && _index.getScoreItems() != null) {
                _scoreItems = new ArrayList<ScoreItemDTO>();
                for(ScoreItem item : _index.getScoreItems()) {
                    _scoreItems.add(new ScoreItemDTO(item, locale, timezone));
                }
            }

        }
    }

    public boolean isCompensated() {
        return _compensated;
    }

    public int getComposite() {
        return (_index != null) ? _index.getCompositeScore() : 0;
    }

    public List<ScoreDTO> getScores() {
        return _scores;
    }

    public List<ScoreItemDTO> getScoreItems() {
        return _scoreItems;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ScoreBean
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Helper bean for individual component scores.
     */
    public static class ScoreDTO {

        String _categoryDisplayName;
        String _category;
        boolean _compensated;
        int _score;
        int _compensatedScore;

        public ScoreDTO(ScoreDefinition def, GenericIndex index) {

            this(def, null, index);
        }

        public ScoreDTO(ScoreDefinition def, ScoreDefinition raw,
                         GenericIndex index) {

            // use the shortest name in the table
            // ScoreItems will be using this too
            // jsl - I think these are both still used in the cert pages
            _category = def.getShortName() != null ? def.getShortName() : def.getName();

            // get the localized name for display
            _categoryDisplayName = def.getDisplayableName();

            // TODO: The score is defaulted to 0 right now to work around some
            // NullPointerExceptions.  This may have to be revisited --Bernie Margolis
            if (raw == null) {
                if (index == null) {
                    _score = 0;
                } else {
                    _score = index.getScore(def.getName());
                }
            } else {
                if (index == null) {
                    _score = 0;
                    _compensatedScore = 0;
                } else {
                    _score = index.getScore(raw.getName());
                    _compensatedScore = index.getScore(def.getName());
                }
                _compensated = true;
            }
        }

        public String getCategory() {
            return _category;
        }

        public boolean isCompensated() {
            return _compensated;
        }

        public int getScore() {
            return _score;
        }

        public int getCompensatedScore() {
            return _compensatedScore;
        }


        public String getCategoryDisplayName() {
            return _categoryDisplayName;
        }
    }
    
    public static class ScoreItemDTO {

        /**
        * The maximum number of characters in the item target.
        */
        public static final int MAX_TARGET = 128;

        private ScoreItem scoreItem;
        private String typeName;
        private Message targetMsg;

        public ScoreItemDTO(ScoreItem scoreItem, Locale locale, TimeZone timezone) {

            this.scoreItem = scoreItem;
            this.typeName = scoreItem.getType();
            
            // kludgey way to make sure acct group DNs display nicely -
            // have to watch this to make sure it doesn't impair performance
            try {
                if ((scoreItem.getTargetMessage().getParameters() != null) &&
                   (scoreItem.getTargetMessage().getParameters().size() == 3)) {
                    List<Object> params = scoreItem.getTargetMessage().getParameters();
                    if (WebUtil.isGroupAttribute((String)params.get(0), (String)params.get(1))) {
                        List<String> displayableNames =  WebUtil.getGroupDisplayableNames(
                            (String)params.get(0), (String)params.get(1), params.get(2));
                        
                        params.set(2, displayableNames);
                        scoreItem.getTargetMessage().setParameters(params);
                    }
                }
            } catch (GeneralException e) {
                // swallow it - no harm in leaving the target msg alone if problems
            }

            // if the target length is greater than the max, truncate it and add ellipses
            if (scoreItem.getTargetMessage() != null){

                // if the message is long, truncate it so we don't flood the ui.
                String msg = scoreItem.getTargetMessage().getLocalizedMessage(locale,
                                                                              timezone);
                if (msg.length() > MAX_TARGET){
                    msg = msg.substring(0, MAX_TARGET - 4);
                    targetMsg = new Message(MessageKeys.MSG_TRUNCATED, msg);
                } else {
                    targetMsg = scoreItem.getTargetMessage();
                }
            }
        }

        public String getTypeName() {
            return typeName;
        }

        public int getCompositePercentage() {
            return scoreItem.getCompositePercentage();
        }

        public int getScore() {
            return scoreItem.getScore();
        }

        public int getScorePercentage() {
            return scoreItem.getScorePercentage();
        }

        public Message getSuggestionMessage() {
            return scoreItem.getSuggestionMessage();
        }

        public Message getTargetMessage() {
            return targetMsg;
        }

        public String getType() {
            return scoreItem.getType();
        }
        
        public String getDisplayName() {
            return (scoreItem.getDisplayName() != null) ? scoreItem.getDisplayName() : getTypeName();
        }

    }

}
