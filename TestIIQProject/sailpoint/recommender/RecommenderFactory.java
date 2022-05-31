/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import sailpoint.Version;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.object.RecommenderDefinition;
import sailpoint.plugin.PluginsUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Get an instance of a Recommender, or information about the currently-selected Recommender
 */
public class RecommenderFactory {

    /**
     * Get the recommendation service using the currently selected Recommender, as set in SystemConfiguration.
     * @param ctx the persistence context
     * @return the recommendation service using the currently selected Recommender, as set in SystemConfiguration.
     * Return null if none is currently selected.
     * @throws GeneralException if a RecommenderDefinition cannot be located using the name
     */
    public static RecommendationService recommendationService(SailPointContext ctx) throws GeneralException {
        String recommenderName = getRecommenderIdOrName();
        if (Util.isNullOrEmpty(recommenderName)) {
            return null;
        }

        RecommenderSPI recommenderSPI = recommenderByIdOrName(ctx, recommenderName);
        if (recommenderSPI == null) {
            return null;
        }

        return new RecommendationService(getRecommenderDefinitionByIdOrName(ctx, recommenderName), recommenderSPI);
    }

    /**
     * Get the name of the currently selected Recommender, as set in SystemConfiguration.
     * @param context the persistence context
     * @return the name of the currently selected Recommender, as set in SystemConfiguration.
     * Return null if none is currently selected.
     * @throws GeneralException if a RecommenderDefinition cannot be located using the name
     */
    public static String recommenderName(SailPointContext context) throws GeneralException {
        RecommenderDefinition recDef = getRecommenderDefinition(context);
        if (recDef == null) {
            return null;
        }

        return recDef.getName();
    }

    /**
     * Get the currently selected RecommenderDefinition, as set in SystemConfiguration.
     * @param context the persistence context
     * @return the currently selected RecommenderDefinition, as set by name in SystemConfiguration.
     * Return null if none is currently selected.
     * @throws GeneralException if a RecommenderDefinition cannot be located using the name
     */
    public static RecommenderDefinition getRecommenderDefinition(SailPointContext context) throws GeneralException {
        return getRecommenderDefinitionByIdOrName(context, getRecommenderIdOrName());
    }

    /**
     * Get the a defined RecommenderDefinition by it's id or name.
     * @param context the persistence context
     * @param recIdOrName the id or name of the recommender
     * @return the requested RecommenderDefinition.
     * Return null if none is currently selected.
     * @throws GeneralException if a RecommenderDefinition cannot be located using the name
     */
    public static RecommenderDefinition getRecommenderDefinitionByIdOrName(
            SailPointContext context, String recIdOrName) throws GeneralException {
        if (Util.isNullOrEmpty(recIdOrName) ) {
            return null;
        }

        QueryOptions ops = new QueryOptions();
        Filter nameFilter = Filter.ignoreCase(Filter.eq("name", recIdOrName));
        Filter idFilter = Filter.ignoreCase(Filter.eq("id", recIdOrName));
        ops.add(Filter.or(nameFilter, idFilter));

        List<RecommenderDefinition> objs = context.getObjects(RecommenderDefinition.class, ops);
        RecommenderDefinition recDef = null;
        if((objs != null) && (!objs.isEmpty())) {
            recDef = objs.get(0);
        } else {
            throw new GeneralException("Cannot find RecommenderDefinition '" + recIdOrName + "'");
        }

        return recDef;
    }

    /**
     * Get an instance of the currently selected Recommender.  This will instantiate an instance of the
     * class configured in the currently selected RecommenderDefinition.
     * @param context the persistence context
     * @return an instance of the currently selected Recommender
     * Return null if none is currently selected.
     * @throws GeneralException if a RecommenderDefinition cannot be located using the name, or the
     * Recommender cannot be instantiated
     */
    public static RecommenderSPI recommender(SailPointContext context) throws GeneralException {
        return recommenderByIdOrName(context, getRecommenderIdOrName());
    }

    /**
     * Get an instance of a defined Recommender by it's id or name.  This will instantiate an instance of the
     * class configured in the specified RecommenderDefinition.
     * @param context the persistence context
     * @param recIdOrName the id or name of the recommender
     * @return an instance of the requested Recommender
     * Return null if none is currently selected.
     * @throws GeneralException if a RecommenderDefinition cannot be located using the name, or the
     * Recommender cannot be instantiated
     */
    public static RecommenderSPI recommenderByIdOrName(SailPointContext context, String recIdOrName) throws GeneralException {

        RecommenderDefinition recDef = getRecommenderDefinitionByIdOrName(context, recIdOrName);
        if (recDef == null) {
            return null;
        }

        boolean isIAIRecommender = recDef.getBoolean(RecommenderDefinition.ATT_IS_IAI_RECOMMENDER);
        if (isIAIRecommender) {
            boolean isIAIEnabled = Version.isIdentityAIEnabled();
            if (!isIAIEnabled) {
                // Other possibility was to throw an exception here, but decided
                // that this is equivalent of not having any recommender selected
                return null;
            }
        }

        RecommenderSPI recommender = null;
        String pluginName = recDef.getString(RecommenderDefinition.ATT_PLUGINNAME);
        if(Util.isNullOrEmpty(pluginName)) {
            recommender = instantiateRecommender(recDef);
        } else {
            // TODO: probably need to do something to ensure exception gets logged.  Not sure
            // at present if the belongs here, for if it's done at a higher level.
            recommender = instantiateRecommenderFromPlugin(pluginName, recDef);
        }

        return recommender;
    }

    /**
     * @return True if a recommender has been selected for use. Otherwise, false.
     */
    public static boolean hasRecommenderSelected() {
        return !Util.isNullOrEmpty(getRecommenderIdOrName());
    }

    /**
     * @return the name of the currently-selected Recommender, read from SystemConfiguration
     */
    private static String getRecommenderIdOrName() {
        return Configuration.getSystemConfig().getString(Configuration.RECOMMENDER_SELECTED);
    }

    private static RecommenderSPI instantiateRecommender(RecommenderDefinition recDef) throws GeneralException {
        String className = getRecommenderClassname(recDef);

        Class recommenderClass = null;
        try {
            recommenderClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new GeneralException("Cannot find recommender class " + className);
        }

        // the class should have a constructor that takes a RecommenderDefinition parameter
        Constructor constructor = null;
        try {
            constructor = recommenderClass.getConstructor(RecommenderDefinition.class);
        } catch (NoSuchMethodException e) {
            throw new GeneralException("Cannot find required constructor in class " + className);
        }

        RecommenderSPI recommender = null;
        try {
            recommender = (RecommenderSPI) constructor.newInstance(recDef);
        } catch (InstantiationException e) {
            throw new GeneralException(e);
        } catch (IllegalAccessException e) {
            throw new GeneralException(e);
        } catch (InvocationTargetException e) {
            throw new GeneralException(e);
        }

        return recommender;
    }

    private static RecommenderSPI instantiateRecommenderFromPlugin(String pluginName, RecommenderDefinition recDef) throws GeneralException {
        String className = getRecommenderClassname(recDef);

        Object[] params = new Object[] { recDef };
        Class[] paramTypes = new Class[] {
            RecommenderDefinition.class
        };

        RecommenderSPI recommender = PluginsUtil.instantiateWithException(pluginName, className, Plugin.ClassExportType.RECOMMENDER, params, paramTypes);
        return recommender;
    }

    /**
     *
     * @param recDef a non-null RecommenderDefinition
     * @return the classname field of the given RecommenderDefinition
     * @throws GeneralException if there is no classname set
     */
    private static String getRecommenderClassname(RecommenderDefinition recDef) throws GeneralException {
        String className = recDef.getString(RecommenderDefinition.ATT_CLASSNAME);
        if (Util.isNullOrEmpty(className)) {
            throw new GeneralException("Missing class attribute in RecommenderDefinition '" + recDef.getName() + "'");
        }

        return className;
    }
}
