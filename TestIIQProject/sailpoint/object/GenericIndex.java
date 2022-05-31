/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An extensible model for holding scores and statistics.
 * This is the basis for the GroupIndex, Scorecard, and
 * ApplicationScorecard.
 *
 * Author: Jeff
 *
 * The scorecard model was refactored during 2.5 with the 
 * introduction of application scores.  Application scores are
 * different from the original identity scores in that they are
 * almost always calculated from custom attributes returned by
 * the connector rather than formal parts of our busines process
 * model like role assignments and entitlements.
 *
 * To make this easy to extend we store scores in an XML map
 * and let the Scorer algorithms manage their scores by name.
 *
 * The original Identity Scorecard didn't do it that way, we
 * had a set of specific integer properties that were mapped
 * directly to Hibernate columns.  This was nice for searching
 * but hard to extend.
 *
 * Another thing added in 2.5 was support for "attribute based"
 * identity scores.  These are a lot like application scores in that
 * we calculate them based on a custom attribute of the identity cube.
 * Since we can't know in advaice what all of these attributes are,
 * we need an extensible way to store the scores.  
 * 
 * The tradeoff is that scores aren't searchable.  If that becomes
 * an issue this can extend ExtensibleSailPointObject but that
 * will really complicate score configuration.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The base model for holding scores and statistics.
 * This is the basis for the GroupIndex, Scorecard, and
 * ApplicationScorecard.
 */
@XMLClass
public class GenericIndex
    extends SailPointObject implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // General properties
    //
    
    /**
     * True if a full index cannot be calculated for some reason.
     */
    boolean _incomplete;

    /*
     * Attribute map containing scores and statistics.
     * The values in here are almost always string representations
     * of integers, but let's leave it open in case we need to 
     * calculate List or Date statistics.
     */
    Attributes<String,Object> _attributes;

    //
    // Scores
    //

    /**
     * Composite score calculated from the others.
     * Make this one a real property so it can be used in searches.
     */
    int _compositeScore;

    /**
     * Optional list of objects describing the most significant
     * factors that contributed to the composite score.
     */
    List<ScoreItem> _scoreItems;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public GenericIndex() {
    }

    /**
     * Scorecards do not usually have names, and when they do they
     * are not necessarily unique.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    /**
     * True if a full index could not be calculated for some reason.
     */
    @XMLProperty
    public boolean isIncomplete() {
        return _incomplete;
    }

    public void setIncomplete(boolean b) {
        _incomplete = b;
    }

    /*
     * Attribute map containing scores and statistics.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }
    
    public void setAttributes(Attributes<String,Object> atts) {
        _attributes = atts;
    }

    /**
     * Composite score calculated from the others.
     */
    @XMLProperty
    public int getCompositeScore() {
        return _compositeScore;
    }

    public void setCompositeScore(int i) {
        _compositeScore = i;
    }

    /**
     * Optional list of objects describing the most significant
     * factors that contributed to the composite score.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<ScoreItem> getScoreItems() {
        return _scoreItems;
    }

    public void setScoreItems(List<ScoreItem> items) {
        _scoreItems = items;
    }

    public void addItem(ScoreItem item) {
        if (item != null) {
            if (_scoreItems == null)
                _scoreItems = new ArrayList<ScoreItem>();
            _scoreItems.add(item);
        }
    }

    public void addItems(List<ScoreItem> items) {
        if (items != null) {
            if (_scoreItems == null)
                _scoreItems = new ArrayList<ScoreItem>();
            _scoreItems.addAll(items);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Return a named score as an integer.
     * This is usually what you want.
     */
    public int getScore(String name) {
        int score = 0;
        if (_attributes != null)
            score = _attributes.getInt(name);
        return score;
    }

    public void setScore(String name, int value) {
        if (name != null) {
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(name, Util.itoa(value));
        }
    }

    /**
     * Reset the index after it has been used.
     */
    public void reset() {
        _incomplete = false;
        _attributes = null;
        _compositeScore = 0;
        _scoreItems = null;
    }

    /**
     * Return true if the contents of two indexes differ.
     * Used to decide if scores have changed and another
     * IdentitySnapshot needs to be made.
     */
    public boolean isDifferent(GenericIndex other) {

        // ugly since we're dealing with maps
        boolean diff = _compositeScore != other.getCompositeScore();

        if (!diff)
            diff = !Difference.equal(_attributes, other.getAttributes());

        if (!diff) {
            // ugh, do we want to try and diff the score items, 
            // technically we should since we could end up with
            // identical scores for different reasons

            List<ScoreItem> otherItems = other.getScoreItems();
            if (_scoreItems == null)
                diff = (otherItems != null && otherItems.size() > 0);
            else if (otherItems == null)
                diff = (_scoreItems != null && _scoreItems.size() > 0);
            else if (_scoreItems.size() != otherItems.size()) 
                diff = true;
            else {
                // Ugh, in 3.1 we changed the model to store catalog
                // keys rather than English "short names" from the
                // ScoreDefinition.  Need to upgrade those.  Assume
                // if we're here that the items have to be in the same
                // order so we can just do name comparison.
                for (int i = 0 ; i < _scoreItems.size() ; i++) {
                    ScoreItem ours = _scoreItems.get(i);
                    ScoreItem theirs = otherItems.get(i);
                    // must have a type but avoid NPE
                    String type = ours.getType();
                    if (type != null && !type.equals(theirs.getType())) {
                        diff = true;
                        break;
                    }
                }
            }
        }

        return diff;
    }

}
