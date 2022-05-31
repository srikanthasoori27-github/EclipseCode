/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.quicklink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkCategory;
import sailpoint.object.QuickLinkOptions;
import sailpoint.object.Script;
import sailpoint.object.UIPreferences;
import sailpoint.service.IdentityResetService.Consts.SessionAttributes;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Util.IMatcher;


/**
 * A service class that manages quick links and quick link categories.
 */
public class QuickLinksService {

    private static final Log LOG = LogFactory.getLog(QuickLinksService.class);
    
    private static final String EMPTY_QUICKLINK_CARDS_VALUE = "None";

    private SailPointContext _context;
    private Identity _user;
    private List<String> _dynamicScopeNames;

    /**
     * Cached list of QuickLinkCategories.
     */
    List<QuickLinkCategory> _quickLinkCategories;

    /**
     * Cached Map of QuickLinkCategory name to the list of QuickLinkWrappers.
     */
    Map<String, List<QuickLinkWrapper>> _quickLinks;

    /**
     * Cached copy of quick links that the user can make for themself.
     */
    List<QuickLink> _selfQuickLinks;

    /**
     * Cached copy of quick links that the user can make for others.
     */
    List<QuickLink> _othersQuickLinks;


    /**
     * Constructor.
     *
     * @param context  The SailPointContext to use.
     * @param user     The Identity we are retrieving quick links for.
     * @param session  A map that is expected to contain the dynamic scope names in the
     *                 ATT_DYNAMIC_SCOPES attribute.
     */
    @SuppressWarnings("unchecked")
    public QuickLinksService(SailPointContext context, Identity user, Map<String,Object> session) {
        this(context, user, (List<String>) session.get(SessionAttributes.ATT_DYNAMIC_SCOPES.value()));
    }

    /**
     * Constructor 
     *
     * @param context  The SailPointContext to use.
     * @param user     The Identity we are retrieving quick links for.
     * @param dynamicScopes List of dynamic scope names to use.
     */
    public QuickLinksService(SailPointContext context, Identity user, List<String> dynamicScopes) {
        _context = context;
        _user = user;
        _dynamicScopeNames = dynamicScopes;
    }

    /**
     * Return a sorted list of the QuickLinkCategories.
     *
     * @return A sorted list of the QuickLinkCategories.
     */
    @SuppressWarnings("unchecked")
    public List<QuickLinkCategory> getQuickLinkCategories() throws GeneralException {
        if (_quickLinkCategories == null) {
            Configuration config = _context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            _quickLinkCategories = config.getList(Configuration.QUICKLINK_CATEGORIES);
            
            if (_quickLinkCategories != null) {
                Collections.sort(_quickLinkCategories);
            }
        }
        return _quickLinkCategories;
    }

    /**
     * Returns a quicklink from the list of quicklinks available to the user by name.
     *
     * @param name  The name of the QuickLink to return.
     *
     * @return The QuickLink with the given name if it is available to the user, or null otherwise.
     */
    public QuickLink getQuickLink(String name) throws GeneralException {
        // There is a shared QuickLinkWrapper that may appear in multiple
        // categories.  We can just return the first that we bump into.
        if (getQuickLinks() != null) {
            for (List<QuickLinkWrapper> wrappers : getQuickLinks().values()) {
                for (QuickLinkWrapper wrapper : wrappers) {
                    if ((null != wrapper.getQuickLink()) &&
                        Util.nullSafeEq(wrapper.getQuickLink().getName(), name)) {
                        return wrapper.getQuickLink();
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Return a Map of QuickLink category name to a list of the QuickLinkWrappers
     * to be displayed in that category.  The QuickLinks are sorted according to
     * their ordering.
     *
     * @return A Map of QuickLinkCategory name to the list of QuickLinkWrappers.
     */
    public Map<String, List<QuickLinkWrapper>> getQuickLinks() throws GeneralException {
        return getQuickLinks(false);
    }

    /**
     * Return a Map of QuickLink category name to a list of the QuickLinkWrappers
     * to be displayed in that category.  The QuickLinks are sorted according to
     * their ordering.
     *
     * @param includeHidden If true, include quicklinks marked as hidden.
     * @return A Map of QuickLinkCategory name to the list of QuickLinkWrappers.
     */
    public Map<String, List<QuickLinkWrapper>> getQuickLinks(boolean includeHidden) throws GeneralException {
        if (_quickLinks == null) {
            _quickLinks = new HashMap<String, List<QuickLinkWrapper>>();

            List<QuickLinkOptions> quickLinkOptions =
                (new QuickLinkOptionsConfigService(_context)).getQuickLinkOptions(_dynamicScopeNames, includeHidden);
            /** Loop through the quicklink categories and build a list to store the links in **/
            for(QuickLinkCategory category : Util.safeIterable(getQuickLinkCategories())) {
                List<QuickLinkWrapper> wrapperList = new ArrayList<QuickLinkWrapper>();
                
                // add the options to the wrapper if the categories match
                for (QuickLinkOptions option : Util.safeIterable(quickLinkOptions)) {
                    if (isSameCategory(category, option)) {
                        merge(wrapperList, option);
                    }
                    
                }
                
                _quickLinks.put(category.getName(), wrapperList);
            }
            
            // run all the label scripts after fully merged, so all settings in wrappers are correct
            runLabelScripts(_quickLinks);
            
            // sort the quicklinks in each category
            for (List<QuickLinkWrapper> value : _quickLinks.values()) {
                Collections.sort(value, new Comparator<QuickLinkWrapper>() {
                    @Override
                    public int compare(QuickLinkWrapper a, QuickLinkWrapper b) {
                        return Util.nullSafeCompareTo(a.getQuickLink().getOrdering(), b.getQuickLink().getOrdering());
                    }
                });
            }
        }
        
        return _quickLinks;
    }

    /**
     * Return a list of QuickLinkCards to be displayed on the mobile home page.
     *
     * @return A non-null list of QuickLinkCards.
     */
    public List<QuickLinkCard> getMobileQuickLinks() throws GeneralException {
        return getQuickLinkCardsFromSysConfig(Configuration.MOBILE_QUICKLINK_CARDS);
    }

    /**
     * Get the full list of QuickLinkCards for the user
     *
     * @return List of QuickLinkCards
     * @throws GeneralException
     */
    public List<QuickLinkCard> getQuickLinkCards() throws GeneralException {
        List<QuickLinkCard> quickLinkCards = new ArrayList<QuickLinkCard>();

        Map<String, List<QuickLinkWrapper>> quickLinks = getQuickLinks();

        for (List<QuickLinkWrapper> quickLinkWrapperList : quickLinks.values()) {
            for (QuickLinkWrapper quickLinkWrapper : Util.safeIterable(quickLinkWrapperList)) {
                quickLinkCards.add(new QuickLinkCard(quickLinkWrapper));
            }
        }

        return quickLinkCards;
    }

    /**
     * Get the QuickLinkCard for a given quickLink
     *
     * @param qlName
     * @return QuickLinkCard
     * @throws GeneralException
     */
    public QuickLinkCard getQuickLinkCard(String qlName) throws GeneralException {
        QuickLinkCard qlCard = null;
        Map<String, List<QuickLinkWrapper>> quickLinks = getQuickLinks();

        for (List<QuickLinkWrapper> quickLinkWrapperList : quickLinks.values()) {
            for (QuickLinkWrapper quickLinkWrapper : Util.safeIterable(quickLinkWrapperList)) {
                if (quickLinkWrapper.getQuickLink().getName().equals(qlName)) {
                    qlCard = new QuickLinkCard(quickLinkWrapper);
                    return qlCard;
                }
            }
        }
        return qlCard;
    }

    /**
     * Get the list of QuickLinkCard for a given QuickLink action
     *
     * @param action quickLink action
     * @return
     * @throws GeneralException
     */
    public List<QuickLinkCard> getQuickLinkCardsForAction(String action) throws GeneralException {
        List<QuickLinkCard> qlCards = new ArrayList<QuickLinkCard>();
        Map<String, List<QuickLinkWrapper>> quickLinks = getQuickLinks();

        for (List<QuickLinkWrapper> quickLinkWrapperList : quickLinks.values()) {
            for (QuickLinkWrapper quickLinkWrapper : Util.safeIterable(quickLinkWrapperList)) {
                if (quickLinkWrapper.getQuickLink().getAction().equals(action)) {
                    qlCards.add(new QuickLinkCard(quickLinkWrapper));
                }
            }
        }
        return qlCards;
    }

    /**
     * Gets the configured quick link cards for the user. Checks UIPreferences first, if not defined then get the default cards.
     *
     * @return list of quick link cards
     * @throws GeneralException
     */
    public List<QuickLinkCard> getConfiguredQuickLinkCards() throws GeneralException {
        String userCardNames = (String)_user.getUIPreference(UIPreferences.PRF_QUICKLINK_CARDS);
        List<QuickLinkCard> quickLinkCards;
        // If its not set, get the defaults
        if (Util.isNullOrEmpty(userCardNames)) {
            quickLinkCards = getQuickLinkCardsFromSysConfig(Configuration.DEFAULT_QUICKLINK_CARDS);
        } else {
            // Check for empty value
            if (EMPTY_QUICKLINK_CARDS_VALUE.equals(userCardNames)) {
                quickLinkCards = new ArrayList<QuickLinkCard>();
            } else {
                // Otherwise, find the quicklinks for the names 
                quickLinkCards = getQuickLinkCards(Util.csvToList(userCardNames));
            }
        }

        return quickLinkCards;
    }

    /**
     * Set the quick link card UI preferences for user
     *
     * @param cards user specified list of quick link cards
     */
    public void setConfiguredQuickLinkCards(List<QuickLinkCard> cards) throws GeneralException {
        String cardNamesCsv;
        // If set to empty, use our special case
        if (Util.isEmpty(cards)) {
            cardNamesCsv = EMPTY_QUICKLINK_CARDS_VALUE;
        } else {
            List<String> cardNames = new ArrayList<String>();
            for (QuickLinkCard card: cards) {
                cardNames.add(card.getName());
            }
            cardNamesCsv = Util.listToCsv(cardNames);
        }

        _user.setUIPreference(UIPreferences.PRF_QUICKLINK_CARDS, cardNamesCsv);

        _context.saveObject(_user);
        _context.commitTransaction();
    }

    /**
     * Get a List of QuickLinkCards for the user that are listed in system config under the given
     * key name.
     *
     * @param  key  The name of the system config key.
     *
     * @return List of QuickLinkCards
     */
    private List<QuickLinkCard> getQuickLinkCardsFromSysConfig(String key) throws GeneralException {
        List<String> quickLinkCardNames = _context.getConfiguration().getStringList(key);
        return getQuickLinkCards(quickLinkCardNames);
    }

    /**
     * Get the list of quick link cards specified in the list of names
     *
     * @param quickLinkCardNames
     * @return list of quick link cards
     */
    private List<QuickLinkCard> getQuickLinkCards(List<String> quickLinkCardNames) throws GeneralException {
        List<QuickLinkCard> quickLinkCards = new ArrayList<QuickLinkCard>();

        if (!Util.isEmpty(quickLinkCardNames)) {
            // QuickLink cards can represent hidden quick links, if specified in the configuration
            Map<String, List<QuickLinkWrapper>> quickLinks = getQuickLinks(true);
            for (Object quickLinkCardName : quickLinkCardNames) {
                QuickLinkCard quickLinkCard = getQuickLinkCard((String)quickLinkCardName, quickLinks);
                if (quickLinkCard != null) {
                    quickLinkCards.add(quickLinkCard);
                }
            }
        }

        return quickLinkCards;
    }

    /**
     * @return QuickLinkCard for the matching QuickLink. If none is found, return null.
     */
    private QuickLinkCard getQuickLinkCard(String name, Map<String, List<QuickLinkWrapper>> quickLinks) throws GeneralException {
        for (List<QuickLinkWrapper> quickLinkWrapperList : quickLinks.values()) {
            for (QuickLinkWrapper quickLinkWrapper : Util.safeIterable(quickLinkWrapperList)) {
                if (name.equals(quickLinkWrapper.getQuickLink().getName())) {
                    return new QuickLinkCard(quickLinkWrapper);

                }
            }
        }

        return null;
    }
        
    private boolean isSameCategory(QuickLinkCategory category, QuickLinkOptions option) {
        return option != null && category != null &&
               option.getQuickLink() != null && option.getQuickLink().getCategory() != null &&
               Util.nullSafeEq(category.getName(), option.getQuickLink().getCategory());
    }
    
    private void merge(List<QuickLinkWrapper> wrapperList, QuickLinkOptions option) {
        if (option == null || option.getQuickLink() == null) {
            return;
        }
        
        // this needs to be final so we can reference it in an anonymous class
        final String qlOptionName = option.getQuickLink().getName();
        QuickLinkWrapper foundWrapper = Util.find(wrapperList, new IMatcher<QuickLinkWrapper>() {
            @Override
            public boolean isMatch(QuickLinkWrapper val) {
                if (val == null || val.getQuickLink() == null || val.getQuickLink().getName() == null) {
                    return false;
                }
                return val.getQuickLink().getName().equals(qlOptionName);
            }
        });
        
        if (foundWrapper == null) { // not found so add it to the list
            wrapperList.add(processQuickLinkOptions(option));
        } else { // merging options to existing quick link wrapper here
            foundWrapper.setAllowSelf( foundWrapper.isAllowSelf() || option.isAllowSelf() );
            foundWrapper.setAllowOthers( foundWrapper.isAllowOthers() || option.isAllowOther() || option.isAllowBulk() );
            foundWrapper.setAllowBulk(foundWrapper.isAllowBulk() || option.isAllowBulk());
        }
    }

    /**
     * Perform some script calculation on the quick link and set the QuickLinkWrapper accordingly.
     *
     * @param options quick link options to process
     *
     * @return a Wrapper object for the quick link options
     */
    private QuickLinkWrapper processQuickLinkOptions(QuickLinkOptions options) {
        if (options == null) {
            return null;
        }
        
        QuickLinkWrapper wrapper = new QuickLinkWrapper(options);
        QuickLink link = options.getQuickLink();
        
        if (link != null) {

            if(link.getArguments() != null) {
                /** The quick link has a script to calculate the count of some objects for the current user **/
                if (Util.atob(link.getArguments().getString(QuickLink.ARG_DISPLAY_COUNT)) ||
                        Util.atob(link.getArguments().getString(QuickLink.ARG_DISPLAY_TEXT))) {
                    Map<String, Object> args = new HashMap<String, Object>();
                    args.put("currentUser", _user);

                    if (link.getArguments().containsKey(QuickLink.ARG_TEXT_SCRIPT)) {
                        Script script = (Script) link.getArguments().get(QuickLink.ARG_TEXT_SCRIPT);
                        try {
                            Object text = _context.runScript(script, args);
                            if (text != null) {
                                wrapper.setText(text.toString());
                            }
                        } catch (GeneralException ge) {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Exception while running script for quicklink: " + link.getName() + ". Exception: " + ge.getMessage(), ge);
                            }
                        }
                    }
                    /** For backwards compatibility **/
                    else if (link.getArguments().containsKey(QuickLink.ARG_COUNT_SCRIPT)) {
                        Script script = (Script) link.getArguments().get(QuickLink.ARG_COUNT_SCRIPT);
                        try {
                            int count = (Integer) _context.runScript(script, args);
                            wrapper.setText(Integer.toString(count));
                        } catch (GeneralException ge) {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Exception while running script for quicklink: " + link.getName() + ". Exception: " + ge.getMessage(), ge);
                            }
                        }
                    }
                }
            }

            //Do not display submenu for Manage User Access or Manage Passwords quicklink
            if(link.getAction()!=null &&
                    (link.getAction().equals(QuickLink.LCM_ACTION_MANAGE_PASSWORDS) ||
                    link.getAction().equals(QuickLink.LCM_ACTION_REQUEST_ACCESS) ||
                    link.getAction().equals(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS) ||
                    link.getAction().equals(QuickLink.LCM_ACTION_EDIT_IDENTITY) ||
                    link.getAction().equals(QuickLink.LCM_ACTION_VIEW_IDENTITY))) {
                wrapper.setAllowSubMenu(false);
            }
        }       
       
        return wrapper;
    }
    
    /**
     * Return a list of all QuickLinks that have a QuickLinkOptions for allowBulk set to true
     * known as allowBulk.
     * @return
     * @throws GeneralException
     */
    public List<QuickLink> getOthersQuickLinks() throws GeneralException {
        if (null == _othersQuickLinks) {
            _othersQuickLinks = getQuickLinksInternal(true);
        }
        return _othersQuickLinks;
    }

    /**
     * Return a list of all QuickLinks that allow requesting for self.
     */
    public List<QuickLink> getSelfQuickLinks() throws GeneralException {
        if (null == _selfQuickLinks) {
            _selfQuickLinks = getQuickLinksInternal(false);
        }
        return _selfQuickLinks;
    }

    /**
     * Return a list of all QuickLinks. There is a flag to specify if the resulting list will include others
     * known as allowBulk.
     * @param others include QuickLinkOptions that have the allowBulk flag set to true
     * @return List of all QuickLinks based on the flags set
     */
    private List<QuickLink> getQuickLinksInternal(boolean others) throws GeneralException {
        boolean self = !others;
        List<QuickLink> links = new ArrayList<QuickLink>();

        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(_context);
        List<QuickLinkOptions> qlOptions = svc.getQuickLinkOptions(_dynamicScopeNames, self, others, true);
        
        for (QuickLinkOptions qlOption : qlOptions) {
            links.add(qlOption.getQuickLink());
        }

        return links;
    }

    /**
     * Run all the optional label scripts on fully initialized quick link wrappers
     */
    private void runLabelScripts(Map<String, List<QuickLinkWrapper>> quickLinks) {

        for (List<QuickLinkWrapper> wrapperList : quickLinks.values()) {
            for (QuickLinkWrapper wrapper: Util.safeIterable(wrapperList)) {
                QuickLink link = wrapper.getQuickLink();
                // Quick link has optional script to use different label 
                if (Util.get(link.getArguments(), QuickLink.ARG_LABEL_SCRIPT) != null) {
                    Script script = (Script)link.getArguments().get(QuickLink.ARG_LABEL_SCRIPT);
                    Map<String, Object> labelArgs = new HashMap<String, Object>();
                    labelArgs.put("quickLinkWrapper", wrapper);
                    try {
                        String scriptLabel = (String)_context.runScript(script, labelArgs);
                        if (!Util.isEmpty(scriptLabel)) {
                            wrapper.setLabel(scriptLabel);
                        }
                    } catch (GeneralException ge) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Exception while running label script for quicklink: " + link.getName() + ". Exception: " + ge.getMessage(), ge);
                        }
                    }
                }
            }
        }
    }
}
