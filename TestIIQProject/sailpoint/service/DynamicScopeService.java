/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.integration.ListResult;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicScope;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLinkOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * A service class that dynamic scopes.
 */
public class DynamicScopeService {

    private SailPointContext _context;


    /**
     * Constructor.
     *
     * @param context  The SailPointContext to use.
     */
    public DynamicScopeService(SailPointContext context) {
        _context = context;
    }

    /**
     * Returns a ListResult of all DynamicScopes with name and id, sorted by name.
     *
     * @return A ListResult of DynamicScopes.
     * @throws GeneralException
     */
    public ListResult getDynamicScopes() throws GeneralException {
        return getDynamicScopes(0,0);
    }
    
    /**
     * Returns a ListResult of DynamicScopes with name and id, sorted by name.
     * 
     * @param start the starting position
     * @param limit the max number of returned value
     * @return A ListResult of DynamicScopes.
     * @throws GeneralException
     */
    public ListResult getDynamicScopes(int start, int limit) throws GeneralException {
        QueryOptions qo = new  QueryOptions();
        qo.setFirstRow(start);
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        qo.setOrderBy("name");
        
        Iterator<Object[]> it = _context.search(DynamicScope.class, qo, "id, name");
        
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (it != null) {
            while ( it.hasNext()) {
                Object[] ds = it.next();
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("id", ds[0]);
                map.put("name",  ds[1]);
                
                result.add(map);
            }
        }
        QueryOptions noLimit = new QueryOptions(qo);
        noLimit.setResultLimit(0);
        int count = _context.countObjects(DynamicScope.class, noLimit);
        ListResult listResult = new ListResult(result, count);
        return listResult;
    }

    /**
     * Gets the DynamicScopeDTO for dynamic scope id.
     * @param dynamicScopeId the id of the dynamic scope (ipop).
     * @return DynamicScopeDTO
     * @throws GeneralException
     */
    public DynamicScopeDTO getDynamicScope(String dynamicScopeId)
            throws GeneralException
    {
        DynamicScope ds = _context.getObjectById(DynamicScope.class, dynamicScopeId);
        if (ds == null) {
            throw new ObjectNotFoundException(DynamicScope.class, dynamicScopeId);
        }

        List<QuickLinkOptions> qloList = this.getQuickLinkOptionsList(ds);

        return new DynamicScopeDTO(ds, qloList, _context);
    }
    
    /**
     * Removes the specified dynamic scope object.
     * @param dynamicScopeId id of the specified dynamic scope
     * @throws GeneralException
     */
    public void deleteDynamicScope(String dynamicScopeId) throws GeneralException
    {
        DynamicScope ds = _context.getObjectById(DynamicScope.class, dynamicScopeId);
        if (ds == null) {
            throw new ObjectNotFoundException(DynamicScope.class, dynamicScopeId);
        }
        
        //this would also delete related QuickLinkOptions, and commit.
        Terminator t = new Terminator(_context);
        t.deleteObject(ds);
    }
    
    /**
     * Creates a new DynamicScope object based on passed in data map.
     * @param data the map 
     * @return DynamicScopeDTO the created DynamicScopeDTO object
     * @throws GeneralException
     */
    public DynamicScopeDTO createDynamicScope(DynamicScopeDTO dto) throws GeneralException 
    {
        validate(dto);
        
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("name", dto.getName()));
        int count = _context.countObjects(DynamicScope.class, qo);

        if (count > 0) {
            throw new GeneralException(MessageKeys.ERR_DYNAMIC_SCOPE_UNIQUE_NAME_REQUIRED);
        }
        
        DynamicScope ds = new DynamicScope();
        dto.copyToDynamicScope(_context, ds);
        
        _context.saveObject(ds);
        
        //save all new QuickLinkOptions
        List<QuickLinkOptions> qloList = dto.getQuickLinkOptionsList();
        for (QuickLinkOptions qlo : Util.safeIterable(qloList)) {
            // update the qlo with the newly saved ds
            qlo.setDynamicScope(ds);
            _context.saveObject(qlo);
        }
        _context.commitTransaction();

        return new DynamicScopeDTO(ds, getQuickLinkOptionsList(ds), _context);
    }

    /**
     * Updates the specified DynamicScope object with passed in data map.
     * @param dynamicScopeId  the id of target DynamicScope object.
     * @param data  the info map
     * @return the updated DynamicScopeDTO object
     * @throws GeneralException
     */
    public DynamicScopeDTO updateDynamicScope(DynamicScopeDTO dto) 
            throws ObjectNotFoundException, GeneralException 
    {
        validate(dto);
        
        DynamicScope ds = _context.getObjectById(DynamicScope.class, dto.getId());
        if (ds == null) {
            throw new ObjectNotFoundException(DynamicScope.class, dto.getId());
        }
        
        dto.copyToDynamicScope(_context, ds);
        _context.saveObject(ds);

        //first remove all existing QuickLinkOptions
        Terminator t = new Terminator(_context);
        List<QuickLinkOptions> existingQloList = this.getQuickLinkOptionsList(ds);
        for (QuickLinkOptions qlo : Util.safeIterable(existingQloList)) {
            //don't remove quicklinkoptions for hidden quicklink
            if (!qlo.getQuickLink().isHidden()) {
                t.deleteObject(qlo);
            }
        }
        
        //then save all new QuickLinkOptions
        List<QuickLinkOptions> qloList = dto.getQuickLinkOptionsList();
        for (QuickLinkOptions qlo : Util.safeIterable(qloList)) {
            if (!qlo.getQuickLink().isHidden()) {
                _context.saveObject(qlo);
            }
        }
        
        _context.commitTransaction();   

        return new DynamicScopeDTO(ds, getQuickLinkOptionsList(ds), _context);
    }

    protected List<QuickLinkOptions> getQuickLinkOptionsList(DynamicScope ds) 
            throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("dynamicScope", ds));

        List<QuickLinkOptions> optionsList = _context.getObjects(QuickLinkOptions.class, qo);
        return optionsList;
    }
    
    protected void validate(DynamicScopeDTO dto) throws ValidationException {
        ValidationException e = null;
        
        if (dto == null) {
            e = updateValidationException(e, MessageKeys.QLP_EDITOR_ERR_OBJECT_REQUIRED);
            // avoid NPE's and bail here
            throw e;
        }
        
        if (Util.isNullOrEmpty(dto.getName())) {
            e = updateValidationException(e, MessageKeys.QLP_EDITOR_NAME_REQUIRED);
        }
        
        Map<String,Object> popMap = dto.getPopulationRequestControlMap();
        // it's okay if the map is empty
        if (!Util.isEmpty(popMap)) {
            
            if (Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL.equals(
                    Util.getString(popMap, DynamicScopeDTO.KEY_POPULATION_DEFINITION_TYPE))) {
                boolean attrControl = Util.getBoolean(popMap, Configuration.LCM_REQUEST_CONTROLS_ENABLE_ATTRIBUTE_CONTROL);
                boolean subControl = Util.getBoolean(popMap, Configuration.LCM_REQUEST_CONTROLS_ENABLE_SUBORDINATE_CONTROL);
                boolean customControl = Util.getBoolean(popMap, Configuration.LCM_REQUEST_CONTROLS_ENABLE_CUSTOM_CONTROL);
                
                // configure one of the 3 enabling options if you configure Specific User types
                if (!attrControl && !subControl && !customControl) {
                    e = updateValidationException(e, MessageKeys.QLP_EDITOR_ERR_SPECIFIC_REQUIRED);
                }
                
                String attrControlFilter = Util.getString(popMap, Configuration.LCM_REQUEST_CONTROLS_ATTRIBUTE_FILTER_CONTROL);
                if (attrControl && (Util.isNullOrEmpty(attrControlFilter) || JsonHelper.emptyObject().equals(attrControlFilter))) {
                    e = updateValidationException(e, MessageKeys.QLP_EDITOR_ERR_ATTRIBUTE_REQUIRED);
                }
                
                if (subControl && Util.isNullOrEmpty(Util.getString(popMap, Configuration.LCM_REQUEST_CONTROLS_SUBORDINATE_CHOICE))) {
                    e = updateValidationException(e, MessageKeys.QLP_EDITOR_ERR_SUBORDINATE_REQUIRED);
                }
                
                if (customControl && Util.isNullOrEmpty(Util.getString(popMap, Configuration.LCM_REQUEST_CONTROLS_CUSTOM_CONTROL))) {
                    e = updateValidationException(e, MessageKeys.QLP_EDITOR_ERR_CUSTOM_REQUIRED);
                }
            }
        }
        
        if (e != null) {
            throw e;
        }
    }
    
    private ValidationException updateValidationException(ValidationException e, String messageKey, Object... args) {
        ValidationException localE = e;
        if (localE == null) {
            localE = new ValidationException(messageKey, args);
        } else {
            localE.addMessage(new Message(Type.Error, messageKey, args));
        }
        
        return localE;
    }

}
