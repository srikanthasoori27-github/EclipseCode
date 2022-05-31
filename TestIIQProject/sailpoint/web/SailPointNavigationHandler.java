/*
   * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
   *
   * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
   *
   * The contents of this file are subject to the terms of either the GNU
   * General Public License Version 2 only ("GPL") or the Common Development
   * and Distribution License("CDDL") (collectively, the "License").  You
   * may not use this file except in compliance with the License.  You can
  * obtain a copy of the License at
  * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
  * or packager/legal/LICENSE.txt.  See the License for the specific
  * language governing permissions and limitations under the License.
  *
  * When distributing the software, include this License Header Notice in each
  * file and include the License file at packager/legal/LICENSE.txt.
  *
  * GPL Classpath Exception:
  * Oracle designates this particular file as subject to the "Classpath"
  * exception as provided by Oracle in the GPL Version 2 section of the License
  * file that accompanied this code.
  *
  * Modifications:
  * If applicable, add the following below the License Header, with the fields
  * enclosed by brackets [] replaced by your own identifying information:
  * "Portions Copyright [year] [name of copyright owner]"
  *
  * Contributor(s):
  * If you wish your version of this file to be governed by only the CDDL or
  * only the GPL Version 2, indicate your decision by adding "[Contributor]
  * elects to include this software in this distribution under the [CDDL or GPL
  * Version 2] license."  If you don't indicate a single choice of license, a
  * recipient has the option to distribute your version of this file under
  * either the CDDL, the GPL Version 2 or to extend the choice of license to
  * its licensees as provided above.  However, if you add GPL Version 2 code
  * and therefore, elected the GPL Version 2 license, then the option applies
  * only if the new code is made subject to such option by the copyright
  * holder.
  */
 
 package sailpoint.web;
 
 import java.io.IOException;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.faces.application.ConfigurableNavigationHandler;
import javax.faces.application.FacesMessage;
import javax.faces.application.NavigationCase;
import javax.faces.application.NavigationHandler;
import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.faces.application.ApplicationAssociate;
import com.sun.faces.util.FacesLogger;
import com.sun.faces.util.MessageUtils;
import com.sun.faces.util.Util;
 
 
 public class SailPointNavigationHandler extends ConfigurableNavigationHandler {
 
     // Log instance for this class
     private static final Logger logger = FacesLogger.APPLICATION.getLogger();
     private static final Log log = LogFactory.getLog(SailPointNavigationHandler.class);
     private NavigationHandler parent;

 
     private volatile NavigationMap navigationMap;

 
     private boolean development;
     private static final Pattern REDIRECT_EQUALS_TRUE = Pattern.compile("(.*)(faces-redirect=true)(.*)");
     private static final Pattern INCLUDE_VIEW_PARAMS_EQUALS_TRUE = Pattern.compile("(.*)(includeViewParams=true)(.*)");
 
 
     // ------------------------------------------------------------ Constructors
 
 
     public SailPointNavigationHandler(final NavigationHandler parent) {
         this.parent = parent;

         this.initializeNavigationFromAssociate();
     }
     
     public SailPointNavigationHandler() {
 
         super();
         if (logger.isLoggable(Level.FINE)) {
             logger.log(Level.FINE, "Created NavigationHandler instance ");
        }
        ApplicationAssociate associate = ApplicationAssociate.getInstance(
              FacesContext.getCurrentInstance().getExternalContext());
        if (associate != null) {
            development = associate.isDevModeEnabled();
        }
 
    }
 

    // ------------------------------ Methods from ConfigurableNavigationHandler

 
    @Override
    public NavigationCase getNavigationCase(FacesContext context, String fromAction, String outcome) {
 
        Util.notNull("context", context);
        NavigationCase result = null;
        CaseStruct caseStruct = getViewId(context, fromAction, outcome);
        if (null != caseStruct) {
            result = caseStruct.navCase;
        }
        
        return result;
        
    }
 
    @Override
    public Map<String, Set<NavigationCase>> getNavigationCases() {
 
        if (navigationMap == null) {
            navigationMap = new NavigationMap();
        }
        return navigationMap;
 
    }
 
 
    // ------------------------------------------ Methods from NavigationHandler

 
    @Override
    public void handleNavigation(FacesContext context, String fromAction, String outcome) {

        if (log.isDebugEnabled()){
            log.debug("Handling navigation - fromAction:" + fromAction + " with outcome:" + outcome);
        }

        // look for parameters
        if (outcome != null && ((outcome.indexOf("?") > -1) || (outcome.indexOf("#") > -1))) {
            handleSpecialNavigation(context, fromAction, outcome);
        }
        else {
            parent.handleNavigation(context, fromAction, outcome);
        }
    }

    // --------------------------------------------------------- Private Methods

    private void handleSpecialNavigation(FacesContext context, String fromAction, String outcome) {
        ExternalContext externalContext = context.getExternalContext();
        String viewId = context.getViewRoot().getViewId();

        List<String> parsed = JSFOutcomeParser.parseOutcome(outcome);
        String parsedOutcome = parsed.get(0);

        CaseStruct caseStruct = findExactMatch(context, viewId, fromAction, parsedOutcome);

        if (caseStruct == null) {
            caseStruct = findWildCardMatch(context, viewId, fromAction, parsedOutcome);
        }

        if (caseStruct == null) {
            caseStruct = findDefaultMatch(context, fromAction, outcome);
        }

        ViewHandler viewHandler = Util.getViewHandler(context);
        String actionUrl = viewHandler.getActionURL(context, caseStruct.viewId);

        String url = actionUrl;

        // Add any special stuff to the end of the URL - note our loop starts on idx 1.
        for (int i=1; i<parsed.size(); i++) {
            url += parsed.get(i);
        }

        if (log.isDebugEnabled()){
            log.debug("Found parameterized action outcome. Redirecting to " + url);
        }

        try {
            externalContext.redirect(externalContext.encodeActionURL(url));
        } catch (IOException e) {
            log.error("NavigationHandler could not handle action outcome '"+outcome+"'", e);
            throw new RuntimeException("NavigationHandler could not handle action outcome '"+outcome+"'");
        }
    }

    private void initializeNavigationFromAssociate() {
 
        ApplicationAssociate associate = ApplicationAssociate.getCurrentInstance();
        if (associate != null) {
            Map<String,Set<NavigationCase>> m = associate.getNavigationCaseListMappings();
            navigationMap = new NavigationMap();
            if (m != null) {
                navigationMap.putAll(m);
            }
        }
 
    }


    private CaseStruct getViewId(FacesContext ctx,
                                 String fromAction,
                                 String outcome) {
 
        if (navigationMap == null) {
            synchronized (this) {
                initializeNavigationFromAssociate();
            }
        }
 
        UIViewRoot root = ctx.getViewRoot();
 
        
        String viewId = (root != null ? root.getViewId() : null);
        
        // if viewIdToTest is not null, use its value to find
        // a navigation match, otherwise look for a match
        // based soley on the fromAction and outcome
        CaseStruct caseStruct = null;
        if (viewId != null) {
            caseStruct = findExactMatch(ctx, viewId, fromAction, outcome);
 
            if (caseStruct == null) {
                caseStruct = findWildCardMatch(ctx, viewId, fromAction, outcome);
            }
        }
 
        if (caseStruct == null) {
            caseStruct = findDefaultMatch(ctx, fromAction, outcome);
        }
        
        // If the navigation rules do not have a match...
        if (caseStruct == null && outcome != null && viewId != null) {
            // Treat empty string equivalent to null outcome.  JSF 2.0 Rev a
            // Changelog issue C063.
            if (caseStruct == null && 0 == outcome.length()) {
                outcome = null;
            } else {
                caseStruct = findImplicitMatch(ctx, viewId, fromAction, outcome);
            }
        }
 
        // no navigation case fo
        if (caseStruct == null && outcome != null && development) {
            String key;
            Object[] params;
            if (fromAction == null) {
                key = MessageUtils.NAVIGATION_NO_MATCHING_OUTCOME_ID;
                params = new Object[] { viewId, outcome };
            } else {
                key = MessageUtils.NAVIGATION_NO_MATCHING_OUTCOME_ACTION_ID;
                params = new Object[] { viewId, fromAction, outcome };
            }
            FacesMessage m = MessageUtils.getExceptionMessage(key, params);
            m.setSeverity(FacesMessage.SEVERITY_WARN);
            ctx.addMessage(null, m);
        }
        return caseStruct;
    }

 
    private CaseStruct findExactMatch(FacesContext ctx,
                                      String viewId,
                                      String fromAction,
                                      String outcome) {
 
        Set<NavigationCase> caseSet = navigationMap.get(viewId);
 
        if (caseSet == null) {
            return null;
        }
 
        // We've found an exact match for the viewIdToTest.  Now we need to evaluate
        // from-action/outcome in the following order:
        // 1) elements specifying both from-action and from-outcome
        // 2) elements specifying only from-outcome
        // 3) elements specifying only from-action
        // 4) elements where both from-action and from-outcome are null
 
 
        return determineViewFromActionOutcome(ctx, caseSet, fromAction, outcome);
    }

 
    private CaseStruct findWildCardMatch(FacesContext ctx,
                                         String viewId,
                                         String fromAction,
                                         String outcome) {
        CaseStruct result = null;
 
        for (String fromViewId : navigationMap.wildcardMatchList) {
            // See if the entire wildcard string (without the trailing "*" is
            // contained in the incoming viewIdToTest.  
            // Ex: /foobar is contained with /foobarbaz
            // If so, then we have found our largest pattern match..
            // If not, then continue on to the next case;
 
            if (!viewId.startsWith(fromViewId)) {
                continue;
            }
 
            // Append the trailing "*" so we can do our map lookup;
 
            String wcFromViewId = new StringBuilder(32).append(fromViewId).append('*').toString();
            Set<NavigationCase> ccaseSet = navigationMap.get(wcFromViewId);
 
            if (ccaseSet == null) {
                return null;
            }
 
            // If we've found a match, then we need to evaluate
            // from-action/outcome in the following order:
            // 1) elements specifying both from-action and from-outcome
            // 2) elements specifying only from-outcome
            // 3) elements specifying only from-action
            // 4) elements where both from-action and from-outcome are null
 
            result = determineViewFromActionOutcome(ctx,
                                                    ccaseSet,
                                                    fromAction,
                                                    outcome);
            if (result != null) {
                break;
            }
        }
        return result;
    }


    private CaseStruct findDefaultMatch(FacesContext ctx,
                                        String fromAction,
                                        String outcome) {
 
        Set<NavigationCase> caseSet = navigationMap.get("*");
 
        if (caseSet == null) {
            return null;
        }
 
        // We need to evaluate from-action/outcome in the follow
        // order:  1)elements specifying both from-action and from-outcome
        // 2) elements specifying only from-outcome
        // 3) elements specifying only from-action
        // 4) elements where both from-action and from-outcome are null
 
        return determineViewFromActionOutcome(ctx, caseSet, fromAction, outcome);
    }


    private CaseStruct findImplicitMatch(FacesContext context,
                                         String viewId,
                                         String fromAction,
                                         String outcome) {
 
        // look for an implicit match.
        String viewIdToTest = outcome;
        String currentViewId = viewId;
        Map<String, List<String>> parameters = null;
        boolean isRedirect = false;
        boolean isIncludeViewParams = false;
 
        int questionMark = viewIdToTest.indexOf('?');
        String queryString;
        if (-1 != questionMark) {
            int viewIdLen = viewIdToTest.length();
            if (viewIdLen <= (questionMark+1)) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "jsf.navigation_invalid_query_string",
                            viewIdToTest);
                }
                if (development) {
                    String key;
                    Object[] params;
                    key = MessageUtils.NAVIGATION_INVALID_QUERY_STRING_ID;
                    params = new Object[]{viewIdToTest};
                    FacesMessage m = MessageUtils.getExceptionMessage(key, params);
                    m.setSeverity(FacesMessage.SEVERITY_WARN);
                    context.addMessage(null, m);
                }
                queryString = null;
                viewIdToTest = viewIdToTest.substring(0, questionMark);
            } else {
                queryString = viewIdToTest.substring(questionMark + 1);
                viewIdToTest = viewIdToTest.substring(0, questionMark);
 
                Matcher m = REDIRECT_EQUALS_TRUE.matcher(queryString);
                if (m.find()) {
                    isRedirect = true;
                    queryString = queryString.replace(m.group(2), "");
                }
                m = INCLUDE_VIEW_PARAMS_EQUALS_TRUE.matcher(queryString);
                if (m.find()) {
                    isIncludeViewParams = true;
                    queryString = queryString.replace(m.group(2), "");
                }
            }
 
            if (queryString != null && queryString.length() > 0) {
                Map<String, Object> appMap = context.getExternalContext().getApplicationMap();
 
                String[] queryElements = Util.split(appMap, queryString, "&amp;|&");
                for (int i = 0, len = queryElements.length; i < len; i ++) {
                    String[] elements = Util.split(appMap, queryElements[i], "=");
                    if (elements.length == 2) {
                        if (parameters == null) {
                            parameters = new LinkedHashMap<String,List<String>>(len / 2, 1.0f);
                            List<String> values = new ArrayList<String>(2);
                            values.add(elements[1]);
                            parameters.put(elements[0], values);
                        } else {
                            List<String> values = parameters.get(elements[0]);
                            if (values == null) {
                                values = new ArrayList<String>(2);
                                parameters.put(elements[0], values);
                            }
                            values.add(elements[1]);
                        }
                    }
                }
            }
        }
 
        // If the viewIdToTest needs an extension, take one from the currentViewId.
        if (viewIdToTest.lastIndexOf('.') == -1) {
            int idx = currentViewId.lastIndexOf('.');
            if (idx != -1) {
                viewIdToTest = viewIdToTest + currentViewId.substring(idx);
            }
        }
 
        if (!viewIdToTest.startsWith("/")) {
            int lastSlash = currentViewId.lastIndexOf("/");
            if (lastSlash != -1) {
                currentViewId = currentViewId.substring(0, lastSlash + 1);
                viewIdToTest = currentViewId + viewIdToTest;
            } else {
                viewIdToTest = "/" + viewIdToTest;
            }
        }
 
        ViewHandler viewHandler = Util.getViewHandler(context);
        viewIdToTest = viewHandler.deriveViewId(context, viewIdToTest);
 
        if (null != viewIdToTest) {
            CaseStruct caseStruct = new CaseStruct();
            caseStruct.viewId = viewIdToTest;
            caseStruct.navCase = new NavigationCase(currentViewId,
                                                    fromAction,
                                                    outcome,
                                                    null,
                                                    viewIdToTest,
                                                    parameters,
                                                    isRedirect,
                                                    isIncludeViewParams);
            return caseStruct;
        }
 
        return null;
 
    }

 
    private CaseStruct determineViewFromActionOutcome(FacesContext ctx,
                                                      Set<NavigationCase> caseSet,
                                                      String fromAction,
                                                      String outcome) {
 
        CaseStruct result = new CaseStruct();
        boolean match = false;
        for (NavigationCase cnc : caseSet) {
            String cncFromAction = cnc.getFromAction();
            String cncFromOutcome = cnc.getFromOutcome();
            boolean cncHasCondition = cnc.hasCondition();
            String cncToViewId = cnc.getToViewId(ctx);
           
            if ((cncFromAction != null) && (cncFromOutcome != null)) {
                if ((cncFromAction.equals(fromAction)) &&
                    (cncFromOutcome.equals(outcome))) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            } else if ((cncFromAction == null) && (cncFromOutcome != null)) {
                if (cncFromOutcome.equals(outcome)) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            } else if ((cncFromAction != null) && (cncFromOutcome == null)) {
                if (cncFromAction.equals(fromAction) && (outcome != null || cncHasCondition)) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            } else if ((cncFromAction == null) && (cncFromOutcome == null)) {
                if (outcome != null || cncHasCondition) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            }
 
            if (match) {
                if (cncHasCondition && Boolean.FALSE.equals(cnc.getCondition(ctx))) {
                    match = false;
                } else {
                    return result;
                }
            }
        }
 
        return null;
    }
 
 
    // ---------------------------------------------------------- Nested Classes
 
 
    private static class CaseStruct {
        String viewId;
        NavigationCase navCase;
    }
 
 
    private static final class NavigationMap extends AbstractMap<String,Set<NavigationCase>> {
 
        private HashMap<String,Set<NavigationCase>> navigationMap =
              new HashMap<String,Set<NavigationCase>>();
        private TreeSet<String> wildcardMatchList =
              new TreeSet<String>(new Comparator<String>() {
                  public int compare(String fromViewId1, String fromViewId2) {
                      return -(fromViewId1.compareTo(fromViewId2));
                  }
              });
 
 
        // ---------------------------------------------------- Methods from Map
 
 
        @Override
        public int size() {
            return navigationMap.size();
        }
 
 
        @Override
        public boolean isEmpty() {
            return navigationMap.isEmpty();
        }
 
        
        @Override
        public Set<NavigationCase> put(String key, Set<NavigationCase> value) {
            if (key == null) {
                throw new IllegalArgumentException(key);
            }
            if (value == null) {
                throw new IllegalArgumentException();
            }
            updateWildcards(key);
            Set<NavigationCase> existing = navigationMap.get(key);
            if (existing == null) {
                navigationMap.put(key, value);
                return null;
            } else {
                existing.addAll(value);
                return existing;
            }
 
        }
 
        @Override
        public void putAll(Map<? extends String, ? extends Set<NavigationCase>> m) {
            if (m == null) {
                return;
            }
            for (Map.Entry<? extends String, ? extends Set<NavigationCase>> entry : m.entrySet()) {
                String key = entry.getKey();
                updateWildcards(key);
                Set<NavigationCase> existing = navigationMap.get(key);
                if (existing == null) {
                    navigationMap.put(key, entry.getValue());
                } else {
                    existing.addAll(entry.getValue());
                }
            }
        }
 
 
        @Override
        public Set<String> keySet() {
            return new AbstractSet<String>() {
 
                @Override
                public String toString() {
                    // Don't let AbstractSet#toString get used. That results in
                    // endless recursion when TracingAspect gets involved                    
                    return navigationMap.keySet().toString();
                }
                
                public Iterator<String> iterator() {
                    return new Iterator<String>() {
 
                        Iterator<Map.Entry<String,Set<NavigationCase>>> i = entrySet().iterator();
 
                        public boolean hasNext() {
                            return i.hasNext();
                        }
 
                        public String next() {
                            return i.next().getKey();
                        }
 
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
 
                public int size() {
                    return NavigationMap.this.size();
                }
            };
        }
        
        @Override
        public Collection<Set<NavigationCase>> values() {
            return new AbstractCollection<Set<NavigationCase>>() {
 
                public Iterator<Set<NavigationCase>> iterator() {
                    return new Iterator<Set<NavigationCase>>() {
 
                        Iterator<Map.Entry<String,Set<NavigationCase>>> i = entrySet().iterator();
 
                        public boolean hasNext() {
                            return i.hasNext();
                        }
 
                        public Set<NavigationCase> next() {
                            return i.next().getValue();
                        }
 
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
 
                public int size() {
                    return NavigationMap.this.size();
                }
            };
        }
 
        public Set<Entry<String, Set<NavigationCase>>> entrySet() {
            return new AbstractSet<Entry<String, Set<NavigationCase>>>() {
 
                @Override
                public String toString() {
                    // Don't let AbstractSet#toString get used. That results in
                    // endless recursion when TracingAspect gets involved
                    return navigationMap.entrySet().toString();
                }
                
                public Iterator<Entry<String, Set<NavigationCase>>> iterator() {
 
                    return new Iterator<Entry<String,Set<NavigationCase>>>() {
 
                        Iterator<Entry<String, Set<NavigationCase>>> i =
                              navigationMap.entrySet().iterator();
 
                        public boolean hasNext() {
                            return i.hasNext();
                        }
 
                        public Entry<String, Set<NavigationCase>> next() {
                            return i.next();
                        }
 
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
 
                public int size() {
                    return NavigationMap.this.size();
                }
            };
        }
 
 
        // ----------------------------------------------------- Private Methods
 
        private void updateWildcards(String fromViewId) {
 
            if (!navigationMap.containsKey(fromViewId)) {
                if (fromViewId.endsWith("*")) {
                    wildcardMatchList.add(fromViewId.substring(0, fromViewId.lastIndexOf('*')));
                }
            }
            
        }
 
    }
 }

          