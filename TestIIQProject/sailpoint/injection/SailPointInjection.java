package sailpoint.injection;

import java.util.Properties;

import sailpoint.messaging.MessagingService;
import sailpoint.messaging.sms.SMSMessage;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

/**
 * Starting point for Injection.
 * This is how you get an instance using injection.
 * <code>
 *   TheInstanceClass myInstance = SailPointInjection.injector().getClass(Interface.class)
 * </code>
 * 
 * @author tapash.majumder
 *
 */
public final class SailPointInjection {

    /**
     * The possible guice environment.. Either development or production. May be we can add Staging etc later.
     * We instantiate different modules for different environment.
     * Each module in turn will instantiate its own set of classes. 
     *
     * Each GuiceEnvironment setting has its own module.
     * We instantiate different modules which in turn 
     */
    public enum GuiceEnvironment {
        
        
        /** Development GuiceEnvironment is linked to {@link DevelopmentModule} class. The set of classes will be different */
        Development(new DevelopmentModule()), 
        
        /** Production will instantiate classes depending on {@link ProductionModule}. */
        Production(new ProductionModule()); 

        /**
         * Name of iiq.properties file
         */
        public static final String GUICE_PROPERTIES_FILE_NAME = "guice.properties";
        /**
         * This the name of the property in iiq.properties file
         * which tells what the current stage is.
         */
        public static final String GUICE_ENV_PROPERTY_NAME = "guiceEnvironment";
        
        /**
         * If no value is set, the default value will be chosen
         */
        public static final GuiceEnvironment DefaultGuiceEnvironment = GuiceEnvironment.Production;
        
        private Module module;

        private GuiceEnvironment(Module module) {
            this.module = module;
        }
        
        public Module getModule() {
            return module;
        }
        
        /**
         * Will parse the string into a Stage value.
         * We can't use Enum.valueOf because it is case sensitive.
         * We cook our own so that it is not case sensitive.
         */
        public static GuiceEnvironment parse(String val) {
            for (GuiceEnvironment stage : values()) {
                if (stage.name().compareToIgnoreCase(val) == 0) {
                    return stage;
                }
            }
            
            return DefaultGuiceEnvironment;
        }

        public static GuiceEnvironment initGuiceEnvironment() {
            try {
                Properties properties = new Properties();
                properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(GUICE_PROPERTIES_FILE_NAME));
                return GuiceEnvironment.parse(properties.getProperty(GUICE_ENV_PROPERTY_NAME, DefaultGuiceEnvironment.name()));
            } catch (Throwable ex) {
                return DefaultGuiceEnvironment;
            }
        }
    }
    
    /**
     * Returns the singleton instance
     */
    public static SailPointInjection instance() {
        return Loader.instance;
    }
    
    /**
     * It will return an injector instance to instantiate injected classes.
     * 
     * The instance is a singleton.
     */
    public static Injector injector() {
        
        return instance().injector;
    }
    
    /**
     * 
     * returns current GuiceEnvironment
     */
    public static GuiceEnvironment getGuiceEnvironment() {
        return instance().guiceEnvironment;
    }

    /**
     * Helper method which gets SMSMessagingService directly without having call inject
     */
    public static MessagingService<SMSMessage> getSMSMessagingService() {
        return injector().getInstance(Keys.SMSMessagingService);
    }

    /**
     * Everything below is PRIVATE.
     */
    
    /**
     * the injector instance.
     */
    private Injector injector;
    
    /**
     * Current stage prod, dev etc.
     */
    private GuiceEnvironment guiceEnvironment;

    
    /**
     * For generic classes these will help getting the instance.
     * SailPointInjection.injector().getInstance(SMSMessagingServiceKey)
     * 
     * Note that these are just shortcuts. We can always use the full Guice documented way
     * viz., SailPointInjection.injector().getInstance(Key.get(new TypeLiteral<MessagingService<SMSMessage>>() {}))
     * 
     * 
     *  This is basically a shortcut to instantiating the TypeLiteral
     *  => Note It is Keys and not Key to prevent name collision.
     */
    private interface Keys {
        /**
         * Shortcut to TypeLiteral to instantiating MessagingService<SMSMessage>
         */
        public static final Key<MessagingService<SMSMessage>> SMSMessagingService = Key.get(new TypeLiteral<MessagingService<SMSMessage>>() {}); 
    }
    
    /**
     * 
     * Why am I using a loader class for the singleton? 
     * 1. Because then the code won't be executed unless an instance is requested.
     * 2. Please also note that synchronized even double checked pattern singleton creation is not
     * completely threadsafe. This is guaranteed to be threadsafe.
     * 
     * @author tapash.majumder
     *
     */
    private static class Loader {
        /**
         * This will only be called when Loader is called. 
         */
        private static final SailPointInjection instance = new SailPointInjection();
    }

    /**
     * Private constructor. No initialization from outside
     */
    private SailPointInjection() {
        
        guiceEnvironment = GuiceEnvironment.initGuiceEnvironment();
        injector = Guice.createInjector(guiceEnvironment.getModule());
    }
}
