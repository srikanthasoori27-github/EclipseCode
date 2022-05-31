package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.Util;
import sailpoint.tools.LazyLoad.ILazyLoader;

/**
 * Helper class initialized by IdentityDTO to
 * show View Identity -> User rights -> Indirect
 * scopes and capabilities
 * 
 */
public class IndirectHelper {
    
    private IdentityDTO parent;
    private LazyLoad<List<IndirectBean>> indirectCapabilities;
    private LazyLoad<List<IndirectBean>> indirectScopes;
    
    public IndirectHelper(IdentityDTO parent) {
        this.parent = parent;
        
        this.indirectCapabilities = new LazyLoad<List<IndirectBean>>(new ILazyLoader<List<IndirectBean>>() {
            public List<IndirectBean> load() throws GeneralException {
                return fetchIndirectCapabilities();
            }
        });

        this.indirectScopes = new LazyLoad<List<IndirectBean>>(new ILazyLoader<List<IndirectBean>>() {
            public List<IndirectBean> load() throws GeneralException {
                return fetchIndirectScopes();
            }
        });
    }

    public Locale getLocale() {
        return this.parent.getLocale();
    }

    public List<IndirectBean> getIndirectCapabilities() throws GeneralException {
        return this.indirectCapabilities.getValue();
    }

    public List<IndirectBean> getIndirectScopes() throws GeneralException {
        return this.indirectScopes.getValue();
    }
    
    private List<IndirectBean> fetchIndirectCapabilities() throws GeneralException {
        List<IndirectBean> list = null;
        Map<String, IndirectBean> map = new TreeMap<String,IndirectBean>();
        Identity ident = this.parent.getObject();
        if (ident != null) {
            List<Identity> wgs = ident.getWorkgroups();

            if ( Util.size(wgs) > 0 ) {
                for ( Identity wg : wgs ) {
                    List<Capability> caps = wg.getCapabilities();
                    if ( Util.size(caps) > 0 ) {
                        for ( Capability cap : caps ) {
                            IndirectBean bean = map.get(cap.getName());
                            if ( bean == null ) 
                                bean = new IndirectBean((SailPointObject)cap, wg, getLocale());
                            else 
                                bean.add(wg);
                            map.put(cap.getName(), bean);
                        }
                    }
                }
            }

            list = new ArrayList<IndirectBean>(map.values());
        }
        return list;
    }

    private List<IndirectBean> fetchIndirectScopes() throws GeneralException {
        List<IndirectBean> list = null;
        Map<String, IndirectBean> map = new TreeMap<String,IndirectBean>();
        Identity ident = this.parent.getObject();
        if (ident != null) {
            List<Identity> wgs = ident.getWorkgroups();
            if ( Util.size(wgs) > 0 ) {
                for ( Identity wg : wgs ) {
                    List<Scope> scopes = wg.getControlledScopes();
                    if ( Util.size(scopes) > 0 ) {
                        for ( Scope scope: scopes) {
                            String scopeName = scope.getName();
                            IndirectBean bean = map.get(scopeName);
                            if ( bean == null ) 
                                bean = new IndirectBean((SailPointObject)scope, wg, getLocale());
                            else 
                                bean.add(wg);
                            map.put(scopeName, bean);
                        }
                    }
                }
            }
            list = new ArrayList<IndirectBean>(map.values());
        }
        return list;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IndirectBean - For use when we are displaying the Capabilities and 
    // scopes assigned to a user indirectly by a workgroup membership.
    //
    //////////////////////////////////////////////////////////////////////

    public static class IndirectBean {
        String _name;
        List<String> _assignedBy;

        public IndirectBean(SailPointObject capOrScope, Identity workgroup, Locale locale) { 
            _assignedBy = new ArrayList<String>();
            if( capOrScope instanceof Scope )
                _name = getLocalizedMessage( ( (Scope) capOrScope ).getDisplayableName(), locale);
            else if( capOrScope instanceof Capability )
                _name = getLocalizedMessage( ( (Capability) capOrScope ).getDisplayableName(), locale);
            else if( capOrScope != null )
                _name = capOrScope.getName(); 
            if ( workgroup != null ) 
                add(workgroup);
        }

        public IndirectBean(SailPointObject capOrScope, Identity workgroup) { 
            this(capOrScope, workgroup, Locale.getDefault());
        }

        public void add(Identity workgroup) { 
            if ( workgroup != null )  {
                String name = workgroup.getName();
                if ( !_assignedBy.contains(name) ) {
                    _assignedBy.add(name);
                }
            }
        }

        public String getAssignedBy() {
            Collections.sort(_assignedBy);
            return Util.listToCsv(_assignedBy);
        }

        public String getName() {
            return _name;
        }
        
        /**
         * 
         * @param message this is possibly a message key or an name
         * @return localized message with given locale
         */
        private String getLocalizedMessage( String name, Locale locale) {
            String message = Internationalizer.getMessage(name, locale);
            return (message != null) ? message : name;
        }
    }
}
