package com.acme.training.rest;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.acme.training.util.SearchQuery;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.PluginContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.UserContext;

/**
 * Authorizer which checks to see if the currently logged in user
 * has in effect admin rights to the Search Plugin.
 *
 * @author 
 * 
 */

public class SearchAuthorizer {
    /**
     * The plugin context.
     */
   


}
