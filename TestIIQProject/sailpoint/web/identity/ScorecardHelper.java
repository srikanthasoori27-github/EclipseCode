package sailpoint.web.identity;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.Scorecard;
import sailpoint.service.ScorecardDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;

/**
 * IdentityDTO delegates to this class for
 * View Identity -> Risk Tab scorecard related
 *
 */
public class ScorecardHelper {

    private IdentityDTO parent;
    private LazyLoad<ScorecardDTO> scorecardLoader;
    
    public ScorecardHelper(IdentityDTO parent) {
        this.parent = parent;
        
        this.scorecardLoader = new LazyLoad<ScorecardDTO>(new ILazyLoader<ScorecardDTO>() {

            public ScorecardDTO load() throws GeneralException {
                return fetchScorecard();
            }
        });
    }
    
    public ScorecardDTO getScorecard() throws GeneralException {
        return this.scorecardLoader.getValue();
    }
    
    private ScorecardDTO fetchScorecard() throws GeneralException {
        
        Identity ident = this.parent.getObject();
        if (ident == null) {
            return null;
        }
        Scorecard card = ident.getScorecard();
        if (card == null) {
            return null;
        }

        List<ScoreDefinition> scores = null;

        SailPointContext con = this.parent.getContext();
        ScoreConfig config = con.getObjectByName(ScoreConfig.class,
                ScoreConfig.OBJ_NAME);
        if (config != null)
            scores = config.getIdentityScores();

        return new ScorecardDTO(card, scores, this.parent.getLocale(), this.parent.getUserTimeZone());
    }
}
