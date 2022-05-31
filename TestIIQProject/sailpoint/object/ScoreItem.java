/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding information about one contributing factor
 * to a risk score.  These may be generated as a side effect
 * of scoring, and displayed so that the viewer is given some
 * indiciation of what they can do to lower the score.
 *
 * Author: Jeff
 */

package sailpoint.object;

import org.apache.commons.lang3.builder.EqualsBuilder;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.Message;

/**
 * A class holding information about one contributing factor
 * to a risk score. These can be generated as a side effect
 * of scoring, and displayed so that the viewer is given some
 * indication of what they can do to lower the score.
 */
@XMLClass
public class ScoreItem extends AbstractXmlObject implements IXmlEqualable<ScoreItem> {

    //////////////////////////////////////////////////////////////////////
    //  
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The score type, which will be the "short name" of the 
     * ScoreDefinition object.The full name is not used here
     * since those tend to be too long.
     */
    String _type;

    /**
     * The display name from the ScoreDefinition this came from
     */
    String _displayName;

    /**
     * The actual value contributed to the score for this type by
     * this item.  
     */
    int _score;

    /**
     * Percentage of the component score for this _type contributed
     * by this item. For a type score of 100 with a _score of 50, 
     * the _scorePercentage will be 50. Scorers must be sure that 
     * the ScoreItems they produce do not have a _scorePercentage sum 
     * that exceeds 100.
     */
    int _scorePercentage;

    /**
     * Percentage of the composite score contributed by this item.
     * This tells you how important this item was to the composite score.
     */
    int _compositePercentage;


    /**
     * @exclude
     * @deprecated Deprecated in 3.0, use {@link #_targetMessage}
     */
    @Deprecated
    String _target;

    /**
     * Brief description of the thing on which this item is most
     * closely associated. These are not necessarily names of
     * objects in the database, but should give the user a good idea
     * of what the source of the score is.
     *
     * For BusinessRole scores, this would be the name of the Bundle.
     *
     * For Entitlement scores, this would be the attribute/value
     * or right/target pair.
     *
     * For PolicyViolation scores, this would a combination of the
     * Policy and SODConstraint object names.
     *
     * For Certification scores, it is less clear, it could be the
     * date of the last certification.
     */
    Message _targetMessage;

    /**
     * @exclude
     * @deprecated in 3.0, use {@link #_suggestionMessage}
     */
    @Deprecated
    String _suggestion;

    /**
     * Suggestion on what the user could do to reduce this score.
     * This might not be necessary, at least not on a per-item basis
     * since the suggestions for items of the same type will
     * usually be the same, for example, "remediate this entitlement".
     */
    Message _suggestionMessage;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ScoreItem() {
    }

    public ScoreItem(ScoreDefinition def) {
        
        _type = getItemType(def);
        _displayName = def.getDisplayableName();
    }

    private String getItemType(ScoreDefinition def) {
        String type = def.getShortName();
        if (type == null)
            type = def.getName();
        return type;
    }

    public boolean contentEquals(ScoreItem other) {

        boolean fieldsEqual = new EqualsBuilder()
            .append(getType(), other.getType())
            .append(getScore(), other.getScore())
            .append(getScorePercentage(), other.getScorePercentage()).isEquals();

        if (!fieldsEqual) {
            return false;
        }

        Message thisMessage = getSuggestionMessage();
        Message thatMessage = other.getSuggestionMessage();
        if (thisMessage == thatMessage) {
            return true;
        }
        if (thisMessage == null) {
            return thatMessage == null;
        }
        return thisMessage.getKey().equals(thatMessage.getKey());
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The score type, which will be the "short name" of the 
     * ScoreDefinition object.
     */
    @XMLProperty
    public String getType() {
        return _type;
    }

    public void setType(String s) {
        _type = s;
    }

    /**
     * The display name for the score definition this came from
     */
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }
    
    public void setDisplayName(String s) {
        _displayName = s;
    }
    

    /**
     * The actual value contributed to the score for this type by
     * this item.  
     */
    @XMLProperty
    public int getScore() {
        return _score;
    }

    public void setScore(int i) {
        _score = i;
    }

    /**
     * Percentage of the component score for this type contributed
     * by this item.
     */
    @XMLProperty
    public int getScorePercentage() {
        return _scorePercentage;
    }

    public void setScorePercentage(int i) {
        _scorePercentage = i;
    }

    /**
     * Percentage of the composite score contributed by this item.
     * This tells you how important this item was to the composite score.
     */
    @XMLProperty
    public int getCompositePercentage() {
        return _compositePercentage;
    }

    public void setCompositePercentage(int i) {
        _compositePercentage = i;
    }

    /**
     * @exclude
     * @deprecated use {@link #getTargetMessage()}
     */
    @Deprecated
    @XMLProperty
    public String getTarget() {
        return _target;
    }

    /**
     * @exclude
     * @deprecated use {@link #setTargetMessage(sailpoint.tools.Message)} 
     */
    @Deprecated
    public void setTarget(String s) {
        _target = s;
    }

    /**
     * Brief description of the thing on which this item is most
     * closely associated. These are not necessarily names of
     * objects in the database, but should give the user a good idea
     * of what the source of the score is.
     */
    @XMLProperty
    public Message getTargetMessage() {
        return _targetMessage;
    }

    public void setTargetMessage(Message targetMessage) {
        _targetMessage = targetMessage;
    }

    /**
     * @exclude
     * @deprecated Deprecated in 3.0, use getSuggestionMessage
     */
    @XMLProperty
    public String getSuggestion() {
        return _suggestion;
    }

    /**
     * @exclude
     * @deprecated Deprecated in 3.0, use setSuggestionMessage
     * @param s
     */
    public void setSuggestion(String s) {
        _suggestion = s;
    }

    /**
     * Suggestion on what the user could do to reduce this score.
     * This might not be necessary, at least not on a per-item basis
     * since the suggestions for items of the same type will
     * usually be the same, for example, "remediate this entitlement".
     */
    @XMLProperty
    public Message getSuggestionMessage() {
        return _suggestionMessage;
    }

    public void setSuggestionMessage(Message suggestionMessage) {
        this._suggestionMessage = suggestionMessage;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if this item is related to  a given ScoreDefinition.
     * Currently it is required that the score item type be the definition name.
     */
    public boolean isRelated(ScoreDefinition def) {

        return (_type != null && _type.equals(getItemType(def)));
    }

 }
