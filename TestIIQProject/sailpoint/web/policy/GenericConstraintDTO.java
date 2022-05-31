/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO for GenericConstraint objects.
 *
 * Author: Jeff
 *
 * There is an arguments map in GenericConstraints that we're not going
 * to expose for editing.  I'm not sure what that is used for, I guess
 * the simpler polices like Risk and Account and custom policies.
 * If we need to be able to edit these we'll need a Signature.
 * 
 */

package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.BaseConstraint;
import sailpoint.object.GenericConstraint;
import sailpoint.object.IdentitySelector;
import sailpoint.tools.GeneralException;

public class GenericConstraintDTO extends BaseConstraintDTO {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * One of the IdentitySelectorDTO.MATCH_TYPE_ constants.
     * This is given to us at construction and then used whenever we
     * need to create a new IdentitySelectorDTO.
     *
     * Ultimatily this is derived from the policy type but since we
     * don't have a back pointer to the PolicyDTO we have to save
     * it here.
     */
    String _matchType;

    /**
     * Selectors.
     * Normally there will either be one or two of these, but in theory
     * some more complex constraints may want more than two if they
     * need to combine several match types, such as a match list with
     * population, with a script.  In the model, this can all be done
     * within a single IdentitySelector (implicitly and'ing) them but
     * we don't show that in the UI and the model doesn't support some
     * combinations like more than one population.  Just leave it general
     * it may come in handy.
     */
    List<IdentitySelectorDTO> _selectors;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public GenericConstraintDTO() {
    }

    public GenericConstraintDTO(GenericConstraint src) {
        this(src, null);
    }

    public GenericConstraintDTO(GenericConstraint src, String matchType) {

        super(src);

        _matchType = matchType;

        // we'll bootstrap left/right selectors if the page asks for them
        _selectors = new ArrayList<IdentitySelectorDTO>();

        if (src != null) {
            List<IdentitySelector> selectors = src.getSelectors();
            if (selectors != null) {
                for (IdentitySelector sel : selectors)
                    _selectors.add(new IdentitySelectorDTO(sel, matchType));
            }
        }
    }

    /**
     * Clone for editing.
     */
    public GenericConstraintDTO(GenericConstraintDTO src) {
        super(src);

        _matchType = src.getMatchType();
        _selectors = new ArrayList<IdentitySelectorDTO>();
        List<IdentitySelectorDTO> selectors = src.getSelectors();
        if (selectors != null) {
            for (IdentitySelectorDTO sel : selectors)
                _selectors.add(new IdentitySelectorDTO(sel));
        }
    }

    public BaseConstraintDTO clone() {
        return new GenericConstraintDTO(this);
    }

    /**
     * After editing refresh the summary text.
     */
    public void refresh() {
        if (_selectors != null) {
            for (IdentitySelectorDTO sel : _selectors)
                sel.refreshSummary();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getMatchType() {
        return _matchType;
    }

    public List<IdentitySelectorDTO> getSelectors() {
        return _selectors;
    }

    //
    // Convenience properties for EntitlementSOD where
    // we need to bootstrap selectors if we don't have them yet.
    // Hmm, maybe we just ensure that the these are created at construction?
    //

    public IdentitySelectorDTO getSelector(int psn) {

        if (_selectors == null)
            _selectors = new ArrayList<IdentitySelectorDTO>();

        // null fill
        int required = psn + 1;
        while (_selectors.size() < required)
            _selectors.add(null);

        IdentitySelectorDTO dto = _selectors.get(psn);
        if (dto == null) {
            dto = new IdentitySelectorDTO(null, _matchType);
            _selectors.set(psn, dto);
        }

        return dto;
    }

    public IdentitySelectorDTO getSelector() {
        return getSelector(0);
    }

    public IdentitySelectorDTO getLeft() {
        return getSelector(0);
    }

    public IdentitySelectorDTO getRight() {
        return getSelector(1);
    }

    /**
     * getRight above returns a value even if there is no 'right' value.  Use this
     * to check for 'right' in cases that expect null values to stay null
     *
     * @return true if has right otherwise false
     */
    private boolean hasRight() {
        return hasSelector(1);
    }

    /**
     * getLeft above returns a value even if there is no 'left' value.  Use this
     * to check for 'left' in cases that expect null values to stay null
     *
     * @return true if has left otherwise false
     */
    private boolean hasLeft() {
        return hasSelector(0);
    }

    /**
     * Returns true if the constraint has a selector at the given index
     * @param index The index in question
     * @return True if the constraint has a selector at index otherwise false
     */
    private boolean hasSelector(int index) {
        return _selectors != null && _selectors.size() > index;
    }

    @Override
    public Map<String, Object> getJsonMap() {
        Map<String, Object> jsonMap = super.getJsonMap();
        jsonMap.put("left", hasLeft() ? getLeft().getSummary() : null);
        jsonMap.put("right", hasRight() ? getRight().getSummary() : null);
        return jsonMap;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by PolicyDTO during commit to create a new constraint instance
     * in the persistence model.
     */
    public BaseConstraint newConstraint() {
        return new GenericConstraint();
    }

    /**
     * This is called as we click Done from the constraints page to 
     * return to the main policy page.  We're not committing the DTO
     * yet but we do need to do validation where we can.
     */
    public void validate() throws GeneralException {
        super.validate();
        if (_selectors != null) {
            for (IdentitySelectorDTO dto : _selectors)
                dto.validate();
        }
    }

    /**
     * Commit gets a BaseConstraint rather than a GenericConstraint 
     * so PolicyDTO can manage both generic and SOD constraints
     * on the samem list.  Somewhat ugly, think about generics...
     */
    public void commit(BaseConstraint src)
        throws GeneralException {

        super.commit(src);

        GenericConstraint gsrc = (GenericConstraint)src;

        // these are XML so we can just replace the list
        if (_selectors == null || _selectors.size() == 0)
            gsrc.setSelectors(null);
        else {
            List<IdentitySelector> selectors = new ArrayList<IdentitySelector>();
            for (IdentitySelectorDTO dto : _selectors) 
                selectors.add(dto.convert());

            gsrc.setSelectors(selectors);
        }
    }

}
