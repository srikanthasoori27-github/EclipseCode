/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 * An example of a custom scoring algorithm.
 * 
 * Author: Jeff
 *
 * This doesn't really need to be in the system code, but it's handy
 * to have it here so we have a known place to find it when we need
 * to email an example to someone.
 *
 * To register this scorer, create an XML import file and add this:

<ImportAction name='merge'>
  <ScoreConfig name='ScoreConfig'>
    <IdentityScores>

      <!--
         This is the score definition inserted into the single system ScoreConfig object.
         The name of this definition is used as the name of the score stored in the
         scorecard so changing it will lose any existing scores.

         It is recommended that you not use long descriptive names for the name,
         if you need something readable use the displayName.  displayName is shown
         in score tables in the UI so it should still be relatively short.

         The Description element contains a longer description of what the
         score does and will be presented in the UI.

         Weight is the weight this score will have when combined into the
         composite score.  This should be left zero in the import file, then
         adjusted in the UI so that all the weights add up to 100%.
         
         configPage is set if you want to write a custom JSF fragment to allow
         editing of the score parameters.  This is rather involved and not
         described in this example.
      -->
      <ScoreDefinition name='customScore'
        displayName="Custom Score"
        scorer='sailpoint.score.CustomScorer'
        component='true'
        weight='0'>
        <Description>A custom score that does something interesting</Description>

        <!--
           Score definitions can have a map of attributes where you can 
           pass information into the custom Scorer class.  In this example
           we have an argument that is simply the score to return.  Other
           uses for this would be setting the names of identity or link
           attributes you want the Scorer to look at.
         -->
        <Attributes>
          <Map>
            <entry key="fixedScore" value="100"/>
         </Map>
        </Attributes>

      </ScoreDefinition>

    </IdentityScores>
  </ScoreConfig>
</ImportAction>


*/

package sailpoint.score;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

public class CustomScorer extends AbstractScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(CustomScorer.class);

    //
    // Definitions of the arguments that can appear in the ScoreDefinition object.
    //

    /**
     * When set the score will always be this value.
     */
    public static final String ARG_FIXED_SCORE = "fixedScore";

    //
    // Configuration Cache
    //

    /**
     * Set by init() after we've loaded and cached anything we need
     * from the database.
     */
    boolean _initialized;

    /**
     * A copy of ARG_FIXED_SCORE from the ScoreDefinition.
     */
    int _fixedScore;

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public CustomScorer() {
    }

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.  This is used in the calculation of
     * composite scores from an partially refreshed scorecard.
     * The name of the ScoreDefinition object is used as the 
     * key to the score value stored in the index.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {
        int score = 0;
        String name = def.getName();
        if (name != null)
            score = index.getScore(name);
        return score;
    }

    /**
     * Cache configuration.  The scorer can be called many times
     * if it needs to read things from the database it should do this
     * once and cache them in local fields.
     */
    public void init(SailPointContext context, ScoreDefinition def)
        throws GeneralException {

        if (!_initialized) {

            if (def.getName() == null)
                log.error("No score name configured for score!");

            // Dig the fixed score out of the definition and save
            // it in a field.
            _fixedScore = def.getInt(ARG_FIXED_SCORE);

            // ...read other database objects and save them locally

            _initialized = true;
        }

    }

    /**
     * This is where the score is calculated.
     *
     * A context is passed in case you need to read something from the db,
     * but if you read the same objects every time you should do this in the
     * init() method instead and cache it.
     *
     * The config object is the single system ScoreConfig object.  It is not
     * normally used.
     *
     * The def object is the ScoreDefinition containing arguments to the
     * scoring algorithm.
     *
     * The src object is the object being scored.  If this scorer is
     * configured as an identity score this will be an Identity.
     *
     * The index object is where the scores will be stored.  For Identities
     * this will be a Scorecard object which is a subclass of GenericIndex.
     */
    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        // make sure we're configured as an identity score
        if (!(src instanceof Identity))
            throw new GeneralException("IdentityAttributeScorer called with non-Identity");

        Identity identity = (Identity)src;

        // cache configuration if we're being called for the first time
        init(context, def);

        // in this simple scorer we just return a fixed value
        int score = _fixedScore;

        // This is a utility method inherited from AbstractScorer that will
        // ensure that the score is greater or equal to zero and less than 
        // or equal to the maximum allowed score.  It should always be called
        // before storing the score.
        int constrainedScore = constrainScore(config, score);

        // store the constrained score in the index
        String scoreName = def.getName();
        if (scoreName != null) 
            index.setScore(scoreName, constrainedScore);

        // add a single ScoreItem for the summary but only 
        // if we're a component of the composite score
        // this is normally true unless you are writing a pair of
        // scorers, one for the "raw" score and one for the "compensated" score
        // You can add more than one ScoreItem to describe things that went into
        // the calcultaion of the score, these are displayed in a table below
        // the scorecard.

        if (def.isComponent()) {
            ScoreItem item = new ScoreItem(def);
            item.setScore(constrainedScore);
            item.setScorePercentage(100);
            
            // the item message can say something about what this score means
            item.setTargetMessage(new Message("Score was fixed at " + constrainedScore));

            index.addItem(item);
        }
    }

    /**
     * This is used only when subclassing LinkAttributeScorer used for calculating
     * application scores.  For identity scores this always returns null.
     */
    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }
}
